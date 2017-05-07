package com.czp.ulc.common.meta;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.czp.ulc.common.util.Utils;

/**
 * 请添加描述 <li>创建人：Jeff.cao</li> <li>创建时间：2017年5月3日 下午12:40:14</li>
 * 
 * @version 0.0.1
 */

public class MetaReadWriter implements AutoCloseable, Runnable {

	private String baseDir;
	private long eachFileSize;
	private DataWriter nowWriter;
	private ExecutorService worker = Executors.newSingleThreadExecutor();
	private static final Logger log = LoggerFactory.getLogger(MetaReadWriter.class);
	private ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();

	/** 这些信息会存储到索引,为了节省空间,将名称简化为字符 */
	public static final String FILE_NAME = "f";
	public static final String LINE_NO = "l";
	public static final String LINE_SIZE = "s";
	public static final String LINE_POS = "p";

	private static final String SUFIX = ".log";
	private static final String ZIP_SUFIX = ".zip";
	private static final String INDEX_SUFIX = ".index";

	public MetaReadWriter(String baseDir) throws Exception {
		this(baseDir, 1024 * 1024 * 200);
	}

	public MetaReadWriter(String baseDir, long eachFileSize) throws Exception {
		this.baseDir = baseDir;
		this.eachFileSize = eachFileSize;
		this.nowWriter = getCurrentFileWriter(getTodayDir(baseDir));
		this.sched.scheduleAtFixedRate(this, 1000, 10, TimeUnit.MINUTES);
	}

	private DataWriter getCurrentFileWriter(File baseDir) throws Exception {
		int fileNo = -1;
		File nowFile = null;
		for (File item : baseDir.listFiles()) {
			if (!item.getName().endsWith(SUFIX))
				continue;
			if (item.length() >= eachFileSize) {
				ansyComprecessFile(item, true);
			} else {
				nowFile = item;
			}
			fileNo = Math.max(fileNo, getFileNumber(item.getName()));
		}
		fileNo++;
		if (nowFile == null) {
			nowFile = new File(baseDir, fileNo + SUFIX);
		}
		return new DataWriter(nowFile, true);
	}

	private void ansyComprecessFile(File file, boolean delSrc) {
		worker.execute(new Runnable() {
			@Override
			public void run() {
				doCompress(file, getZipFile(file), delSrc);
			}
		});
	}

	private void doCompress(File file, File outPut, boolean delSrc) {
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outPut))) {
			long st1 = System.currentTimeMillis();
			long startPos = 0;
			String line = null;
			byte[] lineSpliter = DataWriter.lineSpliter;
			zos.putNextEntry(new ZipEntry(file.getName()));
			LinkedList<Long> linePointer = new LinkedList<>();
			try (BufferedReader br = Files.newBufferedReader(file.toPath())) {
				while ((line = br.readLine()) != null) {
					byte[] bytes = line.getBytes(DataWriter.UTF8);
					startPos += bytes.length + lineSpliter.length;
					linePointer.add(startPos);
					zos.write(bytes);
					zos.write(lineSpliter);
				}
			}
			long oldSize = file.length();
			long size = outPut.length();
			writeLineIndexFile(file, zos, linePointer);
			boolean del = delSrc ? file.delete() : false;
			long end1 = System.currentTimeMillis();
			log.info("compress[{}],size[{}]->[{}],del[{}],time[{}]ms", file, oldSize, size, del, end1 - st1);
		} catch (Exception e) {
			log.error("fail to compress:" + file, e);
		}
	}

	private void writeLineIndexFile(File file, ZipOutputStream zos, LinkedList<Long> index) throws IOException {
		zos.putNextEntry(new ZipEntry(getIndexFileName(file)));
		for (Long num : index) {
			zos.write(Utils.longToBytes(num));
		}
	}

	private static String getIndexFileName(File file) {
		return file.getName().concat(INDEX_SUFIX);
	}

	private static File getZipFile(File file) {
		return new File(file + ZIP_SUFIX);
	}

	/***
	 * 滚动写文件,返回文件的当前行号
	 * 
	 * @param lines
	 * @return
	 * @throws IOException
	 */
	public synchronized String write(List<String> lines) throws Exception {

		if (nowWriter.getFile().length() >= eachFileSize) {
			Utils.close(nowWriter);
			nowWriter = getCurrentFileWriter(getTodayDir(baseDir));
		}

		int lineSize = 0;
		long lineNo = nowWriter.getlineNo();
		long pointer = nowWriter.getPointer();
		for (String string : lines) {
			String line = string.trim();
			if (line.length() > 0) {
				nowWriter.writeLine(string);
				lineSize++;
			}
		}
		JSONObject json = new JSONObject();
		json.put(LINE_NO, lineNo);
		json.put(LINE_POS, pointer);
		json.put(LINE_SIZE, lineSize);
		json.put(FILE_NAME, nowWriter.getFile());
		return json.toJSONString();
	}

	private Integer getFileNumber(String name) {
		return Integer.valueOf(name.substring(0, name.indexOf(".")));
	}

	private File getTodayDir(String baseDir) {
		Date day = Utils.igroeHMSTime(System.currentTimeMillis());
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
		String fileStr = format.format(day);
		File file = new File(baseDir, fileStr);
		file.mkdirs();
		return file;
	}

	// 先读取索引,在根据索引快速定位行对应的文件指针读取行
	private static long[] getLinePointer(ZipFile zf, File file, long lineNo, int lineSize) throws IOException {
		if (lineNo <= 0)
			return new long[] { 0, 0 };

		ZipEntry indexFile = zf.getEntry(getIndexFileName(file));
		try (BufferedInputStream bis = new BufferedInputStream(zf.getInputStream(indexFile))) {
			int bytes = Long.BYTES;
			bis.skip((lineNo - 1) * bytes);
			byte[] buf = new byte[bytes * lineSize];
			bis.read(buf);
			ByteBuffer b = ByteBuffer.wrap(buf, 0, bytes);
			ByteBuffer b2 = ByteBuffer.wrap(buf, (lineSize - 1) * bytes, bytes);
			long fristLineOffset = b.getLong();
			long lastLineOffset = b2.getLong();
			return new long[] { fristLineOffset, lastLineOffset - fristLineOffset };
		}
	}

	@Override
	public void close() throws Exception {
		Utils.close(nowWriter);
		worker.shutdown();
		sched.shutdown();
	}

	public static Map<Long, String> readFromUnCompressFile(InputStream is, Set<JSONObject> lineRequest)
			throws IOException {
		// 纪录已经跳过的字节数
		long lastSkip = 0;
		// 纪录已经读取的字节数
		long hasReadSize = 0;
		// 映射行号和内容
		Map<Long, String> lineMap = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
			for (JSONObject json : lineRequest) {
				int size = json.getIntValue(LINE_SIZE);
				long lineNo = json.getLongValue(LINE_NO);
				long linePos = json.getLongValue(LINE_POS);
				long skip = linePos - lastSkip - hasReadSize;
				long realSkip = 0;
				while (realSkip < skip)
					realSkip += is.skip(skip - realSkip);

				String line;
				StringBuilder sb = new StringBuilder();
				while (size-- > 0 && (line = br.readLine()) != null) {
					sb.append(line).append(DataWriter.LINE_SPLITER);
					hasReadSize += line.length();
				}
				lineMap.put(lineNo, sb.toString());
				lastSkip = skip;
			}
		}
		return lineMap;
	}

	// 合并读取,避免同一个文件打开关闭多次
	public static Map<String, Map<Long, String>> mergeRead(List<JSONObject> lineRequest) throws Exception {
		Map<String, Map<Long, String>> datas = new HashMap<>();
		Map<String, Set<JSONObject>> readLines = classifyByFile(lineRequest);
		for (Entry<String, Set<JSONObject>> entry : readLines.entrySet()) {
			Set<JSONObject> jsons = entry.getValue();
			String fileName = entry.getKey();
			File file = new File(fileName);
			Map<Long, String> lines = null;
			String logName = fileName;

			long st = System.currentTimeMillis();
			if (file.exists()) {
				lines = readFromUnCompressFile(new FileInputStream(file), jsons);
			} else {
				lines = readFromCompressFile(jsons, file);
				logName = getZipFile(file).getName();
			}
			datas.put(fileName, lines);
			long end = System.currentTimeMillis();
			log.info("read[{}]lines,from[{}],time:[{}]ms", jsons.size(), logName, end - st);
		}
		return datas;
	}

	private static Map<Long, String> readFromCompressFile(Set<JSONObject> jsons, File file) throws Exception {
		File zipFile = getZipFile(file);
		Map<Long, String> linesMap = new HashMap<>();
		try (ZipFile zf = new ZipFile(zipFile)) {
			ZipEntry logFile = zf.getEntry(file.getName());
			try (BufferedInputStream br = new BufferedInputStream(zf.getInputStream(logFile))) {
				long lastSkip = 0, hasReadSize = 0;
				for (JSONObject json : jsons) {
					int size = json.getIntValue(LINE_SIZE);
					long lineNo = json.getLongValue(LINE_NO);
					long[] linePointer = getLinePointer(zf, file, lineNo, size);
					if (linePointer[0] <= 0) {
						log.error("can't find index for line:{} in:{}", lineNo, zipFile);
						linesMap.put(lineNo, "N/A");
					} else {
						long st = System.currentTimeMillis();
						long skip = linePointer[0] - lastSkip - hasReadSize;
						long realSkip = 0;
						while (realSkip < skip)
							realSkip += br.skip(skip - realSkip);

						long end = System.currentTimeMillis();
						log.info("skip:{} bytes from:{} time:{}ms", skip, zipFile, end - st);
						byte[] buf = new byte[(int) linePointer[1]];
						br.read(buf);
						linesMap.put(lineNo, new String(buf, DataWriter.UTF8));
						hasReadSize += buf.length;
						lastSkip = skip;
					}
				}
			}
		}
		return linesMap;
	}

	// 把读请求按文件分类,这样一个文件只打开一次
	private static Map<String, Set<JSONObject>> classifyByFile(List<JSONObject> lineRequest) {
		Map<String, Set<JSONObject>> readLines = new HashMap<>();
		for (JSONObject json : lineRequest) {
			String fileName = (String) json.remove(FILE_NAME);
			Set<JSONObject> lineNos = readLines.get(fileName);
			if (lineNos == null) {
				lineNos = new TreeSet<>(new Comparator<JSONObject>() {
					public int compare(JSONObject o1, JSONObject o2) {
						return o1.getInteger(LINE_NO).compareTo(o2.getInteger(LINE_NO));
					}
				});
				readLines.put(fileName, lineNos);
			}
			lineNos.add(json);
		}
		return readLines;
	}

	@Override
	public void run() {
		// 定时检查非今天的目录下是否有未压缩的文件
		File base = new File(baseDir);
		File tadayFile = getTodayDir(baseDir);
		for (File file : base.listFiles()) {
			if (file.getName().equals(tadayFile))
				continue;
			for (File item : file.listFiles()) {
				if (!item.getName().endsWith(SUFIX))
					continue;
				if (item.length() >= eachFileSize) {
					ansyComprecessFile(item, true);
					log.info("find required file:{},will compress", item);
				}
			}
		}
	}
}
