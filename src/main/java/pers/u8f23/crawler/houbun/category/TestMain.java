package pers.u8f23.crawler.houbun.category;

import lombok.extern.slf4j.Slf4j;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

/**
 * @author 8f23
 * @create 2023/7/2-22:32
 */
@Slf4j
public class TestMain
{
	private static final Map<String, Set<String>> result = new HashMap<>();

	public static void main(String[] args) throws InterruptedException,
		UnsupportedEncodingException
	{
		String src = "/%E8%8A%B3%E6%96%87%E7%A4%BE%E8%91%97%E5%90%8D%E5%A5%B3%E6%BC%94%E5%91%98";
		String res = URLDecoder.decode(src, "UTF-8");
		log.info(res);
	}

}
