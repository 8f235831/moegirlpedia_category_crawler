package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;

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
	private static final String OUTPUT_FILE_PATH =
		"D:\\Myworks\\Java\\20230702-01-moegirlpedia-houbun-category\\output\\result_%s.json";
	private static final String OUTPUT_SIMPLIFIED_FILE_PATH =
		"D:\\Myworks\\Java\\20230702-01-moegirlpedia-houbun-category\\output\\simplified_%s.txt";
	private static final String BACKUP_FILE_PATH =
		"D:\\Myworks\\Java\\20230702-01-moegirlpedia-houbun-category\\output\\backup_%s.xml";

	public static void main(String[] args)
	{
		RxJavaPlugins.setErrorHandler((th) -> log.info("global rxjava error catch: ", th));
		String timeStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		new Crawler(
			ROOT_PAGE_TITLE,
			String.format(OUTPUT_FILE_PATH, timeStr),
			String.format(OUTPUT_SIMPLIFIED_FILE_PATH, timeStr),
			String.format(BACKUP_FILE_PATH, timeStr)
		);
	}
}
