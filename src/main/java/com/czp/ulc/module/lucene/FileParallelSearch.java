/* 
 * 创建日期 2016-11-10
 *
 * 成都澳乐科技有限公司版权所有
 * 电话：028-85253121 
 * 传真：028-85253121
 * 邮编：610041 
 * 地址：成都市武侯区航空路6号丰德国际C3
 */
package com.czp.ulc.module.lucene;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.czp.ulc.core.ThreadPools;
import com.czp.ulc.core.bean.LuceneFile;
import com.czp.ulc.core.cache.SoftRefMap;
import com.czp.ulc.core.dao.LuceneFileDao;
import com.czp.ulc.module.lucene.search.SearchCallback;
import com.czp.ulc.web.QueryCondtion;

/**
 * Function:并行搜索
 *
 * @date:2017年5月27日/下午2:37:04
 * @Author:jeff.cao@aoliday.com
 * @version:1.0
 */
public class FileParallelSearch {

	/** 包装原始的IndexSearcher支持检测索引文件是否修改 */
	private static class IndexSearcherWrapper {
		IndexSearcher search;
		long lastModifyTime;
		File indexFile;

		public IndexSearcherWrapper(IndexSearcher search, long lastModifyTime, File indexFile) {
			this.search = search;
			this.lastModifyTime = lastModifyTime;
			this.indexFile = indexFile;
		}

		public boolean isChanged() {
			return indexFile.lastModified() > lastModifyTime;
		}
	}

	private LuceneFileDao lFileDao;
	private ExecutorService worker;
	private String prefix = IndexFileNames.SEGMENTS + "_";
	private SoftRefMap<File, IndexSearcherWrapper> dirMap = new SoftRefMap<>();

	private static final Logger LOG = LoggerFactory.getLogger(FileParallelSearch.class);

	public FileParallelSearch(LuceneFileDao lFileDao) {
		this.lFileDao = lFileDao;
		int threadSize = LuceneConfig.PARALLEL_SEARCH_THREADS;
		this.worker = ThreadPools.getInstance().newPool("ParallelSearch", threadSize);
	}

	private void asynSearch(AtomicBoolean isBreak, AtomicLong matchs, AtomicInteger waitSeachNum, File indexFile,
			SearchCallback search, String host, long total) {
		worker.execute(() -> {
			try {
				QueryCondtion cdt = search.getQuery();
				Set<String> feilds = search.getFeilds();
				Query realQuery = removeHostParam(search);
				IndexSearcher searcher = getCachedSearcher(indexFile, host);
				TopDocs docs = searcher.search(realQuery, cdt.getSize());
				matchs.getAndAdd(docs.totalHits);
				for (ScoreDoc scoreDoc : docs.scoreDocs) {
					Document doc = searcher.doc(scoreDoc.doc, feilds);
					String file = doc.get(DocField.FILE);
					String line = doc.get(DocField.LINE);
					if (!search.handle(host, file, line)) {
						isBreak.set(true);
						break;
					}
				}
				LOG.info("finish search in {} total {}", indexFile, docs.totalHits);
			} catch (Exception e) {
				LOG.error("ansy sear error", e);
			} finally {
				if (isBreak.get() || waitSeachNum.decrementAndGet() == 0)
					search.onFinish(total, matchs.get());
			}
		});

	}

	public void shutdown() {
		worker.shutdownNow();
	}

	public void search(SearchCallback search, long allDocs, int memMatch) {
		AtomicLong match = new AtomicLong(memMatch);
		AtomicBoolean isBreak = new AtomicBoolean();
		AtomicInteger waitSeachNum = new AtomicInteger();
		try {
			List<LuceneFile> dirs = findMatchDir(search);
			waitSeachNum.set(dirs.size());

			for (LuceneFile item : dirs) {
				String host = item.getServer();
				File file = new File(item.getPath());
				asynSearch(isBreak, match, waitSeachNum, file, search, host, allDocs);
			}
		} catch (Exception e) {
			isBreak.set(true);
			LOG.error("search erro", e);
		} finally {
			if (isBreak.get() || waitSeachNum.get() == 0)
				search.onFinish(allDocs, match.get());
		}

	}

	private IndexSearcher getCachedSearcher(File file, String host) {
		try {
			IndexSearcherWrapper searcher = dirMap.get(file);
			if (searcher != null && !searcher.isChanged()) {
				return searcher.search;
			}
			// 锁住目录
			synchronized (file) {
				// 关闭过期目录
				if (searcher != null && searcher.isChanged()) {
					dirMap.remove(file).search.getIndexReader().close();
				}
				if (!dirMap.containsKey(file)) {
					LOG.info("start open index:{}", file);
					FSDirectory index = FSDirectory.open(file.toPath());
					DirectoryReader newReader = DirectoryReader.open(index);
					IndexSearcher search = new IndexSearcher(newReader);
					dirMap.put(file, new IndexSearcherWrapper(search, file.lastModified(), file));
					LOG.info("sucess to open index:{}", file);
				}
				return dirMap.get(file).search;
			}
		} catch (Exception e) {
			throw new RuntimeException(e.toString(), e.getCause());
		}
	}

	/**
	 * 找到匹配时间点的索引目录
	 * 
	 * @param hosts
	 * @param start
	 * @param end
	 * @return
	 */
	public List<LuceneFile> findMatchDir(SearchCallback searcher) {
		QueryCondtion query = searcher.getQuery();
		List<LuceneFile> files = lFileDao.query(query);
		removeVaildFile(files);
		LOG.info("find:[{}]dirs for:{}", files.size(), searcher.getQuery().getQuery());
		return files;
	}

	// @link DirectoryReader.indexExists
	private void removeVaildFile(List<LuceneFile> next) {
		Iterator<LuceneFile> it = next.iterator();
		while (it.hasNext()) {
			boolean find = false;
			LuceneFile dir = it.next();
			File file = new File(dir.getPath());
			if (!file.exists()) {
				LOG.error("index file not exist:{}", file);
				it.remove();
				continue;
			}
			for (File item : file.listFiles()) {
				if (item.getName().startsWith(prefix)) {
					find = true;
					break;
				}
			}
			if (find == false) {
				LOG.error("segments file not exist:{}", file);
				it.remove();
			}
		}
	}

	public long count(SearchCallback search, Map<String, Long> json) {
		List<LuceneFile> dirs = findMatchDir(search);
		if (dirs.isEmpty())
			return 0;

		AtomicLong count = new AtomicLong();
		CountDownLatch lock = new CountDownLatch(dirs.size());
		for (LuceneFile luceneFile : dirs) {
			executeCountTask(search, json, count, lock, luceneFile);
		}
		try {
			lock.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return count.get();
	}

	private void executeCountTask(SearchCallback search, Map<String, Long> json, AtomicLong count, CountDownLatch lock,
			LuceneFile luceneFile) {
		worker.execute(() -> {
			try {
				String host = luceneFile.getServer();
				String path = luceneFile.getPath();
				Query realQuery = removeHostParam(search);
				IndexSearcher searcher = getCachedSearcher(new File(path), host);
				long match = searcher.count(realQuery);
				json.put(host, match + json.getOrDefault(host, 0L));
				count.getAndAdd(match);
				lock.countDown();
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/***
	 * 文件索引是按主机和时间分类的,索引里并没有host/date信息,所以这里要特殊处理
	 * 
	 * @param search
	 * @return
	 */
	private Query removeHostParam(SearchCallback search) {
		return search.getQuery().buildFileIndexQuery();
	}
}
