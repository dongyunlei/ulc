package com.czp.ulc.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import com.czp.ulc.module.lucene.AnalyzerUtil;
import com.czp.ulc.module.lucene.DocField;
import com.czp.ulc.module.lucene.LogAnalyzer;
import com.czp.ulc.module.lucene.RangeQueryParser;

/**
 * 请添加描述
 * <li>创建人：Jeff.cao</li>
 * <li>创建时间：2017年4月27日 下午2:19:42</li>
 * 
 * @version 0.0.1
 */

public class Lucenetest {

	public static void main(String[] args) throws IOException, ParseException {

		// Analyzer analyzer = new LogAnalyzer();
		// File file = new File("./index-1-unsave");
		// file.mkdirs();
		// FSDirectory open = FSDirectory.open(file.toPath());
		// TieredMergePolicy mergePolicy = new TieredMergePolicy();
		// IndexWriterConfig conf = new IndexWriterConfig(analyzer);
		// conf.setOpenMode(OpenMode.CREATE_OR_APPEND);
		// conf.setMergePolicy(mergePolicy);
		// conf.setUseCompoundFile(true);
		//
		// IndexWriter indexWriter = new IndexWriter(open, conf);
		//
		// String temp = "";
		// String path = "./3.log";
		// long st = System.currentTimeMillis();
		// BufferedReader br = new BufferedReader(new FileReader(new
		// File(path)));
		// while ((temp = br.readLine()) != null) {
		// Document doc = new Document();
		// doc.add(new TextField(DocField.LINE, temp, Field.Store.NO));
		// indexWriter.addDocument(doc);
		// }
		// br.close();
		// indexWriter.commit();
		// indexWriter.close();
		// System.out.println("---index---->" + (System.currentTimeMillis() -
		// st) / 1000.0);
		// IndexSearcher ramSearcher = new
		// IndexSearcher(DirectoryReader.open(open));
		// NumSupportQueryParser parser = new
		// NumSupportQueryParser(DocField.ALL_FEILD, analyzer);
		// TopDocs search =
		// ramSearcher.search(parser.parse("l:jsonexception\\:"), 10);
		// ScoreDoc[] scoreDocs = search.scoreDocs;
		// for (ScoreDoc scoreDoc : scoreDocs) {
		// Document doc = ramSearcher.doc(scoreDoc.doc);
		// System.out.println(doc);
		// }
		// ramSearcher.getIndexReader().close();
		// http://www.itrip.com/login/index?redirect_url=http://pre.itrip.com//ulc/callback?requireToken=1?requireToken=1&allow=aoliday
		// http://www.itrip.com/login/index?redirect_url=http://pre.itrip.com//ulc/callback?requireToken=1?requireToken=1&allow=aoliday
		String x = "{orderId=320232635, itemId=973129, supplier=2138} INFO 2017-09-20 10:28:59 [InterfaceBookingTask.java:47] 接口预定[320232635/973129]开始...";

		String str = AnalyzerUtil.displayToken("/var/www/data/work/itrip_mobile/tomcat7/logs/db.log", new LogAnalyzer());
		String str2 = AnalyzerUtil.displayToken(x, new LogAnalyzer());
		String str3 = AnalyzerUtil.displayToken(x, new StandardAnalyzer());
		System.out.println(str);
		System.out.println(str2);
		System.out.println(str3);
		// String temp = "";
		// String path = "./2.log";
		// BufferedReader br = new BufferedReader(new FileReader(new
		// File(path)));
		// while ( (temp = br.readLine()) != null) {
		// AnalyzerUtil.displayToken(temp, new MyAnalyzer());
		// }
		// br.close();

	}
}
