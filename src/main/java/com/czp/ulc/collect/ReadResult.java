/* 
 * 创建日期 2016-11-10
 *
 * 成都澳乐科技有限公司版权所有
 * 电话：028-85253121 
 * 传真：028-85253121
 * 邮编：610041 
 * 地址：成都市武侯区航空路6号丰德国际C3
 */
package com.czp.ulc.collect;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.czp.ulc.common.bean.HostBean;

/**
 * Function:每次读取到对象
 *
 * @date:2017年3月22日/下午3:37:15
 * @Author:jeff.cao@aoliday.com
 * @version:1.0
 */
public class ReadResult {

	private HostBean host;
	
	private String file;

	private List<String> lines = new LinkedList<String>();

	public ReadResult(HostBean host,String file, List<String> lines) {
		this.file = file;
		this.host = host;
		this.lines = lines;
	}

	public String getFile() {
		return file;
	}

	public List<String> getLines() {
		//Collections.unmodifiableList(lines);
		return lines;
	}
	

	public HostBean getHost() {
		return host;
	}

	@Override
	public String toString() {
		Iterator<String> iterator = lines.iterator();
		StringBuffer sb = new StringBuffer();
		sb.append(file).append("\n");
		while (iterator.hasNext()) {
			sb.append("  ").append(iterator.next()).append("\n");
		}
		return sb.toString();
	}

}
