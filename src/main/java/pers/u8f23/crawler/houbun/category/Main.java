package pers.u8f23.crawler.houbun.category;

import lombok.extern.slf4j.Slf4j;
import pers.u8f23.crawler.houbun.category.core.Crawler;

/**
 * 入口主函数。
 *
 * @author 8f23
 * @create 2023/4/15-11:57
 */
@Slf4j
public class Main
{
	private static final String ROOT_PAGE_TITLE = "Category:芳文社";
	private static final String OUTPUT_FILE_PATH = "./output/result.json";

	public static void main(String[] args)
	{
		new Crawler(ROOT_PAGE_TITLE, OUTPUT_FILE_PATH);
	}
}
