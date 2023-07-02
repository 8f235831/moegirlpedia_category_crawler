package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import pers.u8f23.crawler.houbun.category.core.TaskObserver;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author 8f23
 * @create 2023/7/2-22:32
 */
@Slf4j
public class TestMain
{
	private static final Map<String, Set<String>> result = new HashMap<>();

	public static void main(String[] args) throws InterruptedException
	{

		HttpService.getInstance()
			.get("Category:魔法少女小圆")
			.subscribeOn(Schedulers.single())
			.observeOn(Schedulers.newThread())
			.subscribe(new TaskObserver<>(
				parseCatePage(
					s -> log.info("cate:{}", s),
					s -> log.info("page:{}", s)
				),
				() -> {},
				() -> {}
			));
		Thread.sleep(Long.MAX_VALUE);
	}

	private static Consumer<Response<ResponseBody>> parseCatePage(
		Consumer<String> handleSubCate, Consumer<String> handleSubPage)
	{
		return (response) -> {
			if (response == null)
			{
				return;
			}
			ResponseBody body = response.body();
			if (body == null)
			{
				return;
			}
			String content = null;
			try
			{
				content = body.string();
			}
			catch (IOException ignored)
			{
			}
			if (content == null)
			{
				return;
			}
			Document document = Jsoup.parse(content);
			List<String> subCateTitles =
				findAllLinks(document.select("#mw-subcategories").first());
			subCateTitles.forEach(handleSubCate);
			List<String> subPageTitles =
				findAllLinks(document.select("#mw-pages").first());
			subPageTitles.forEach(handleSubPage);

			log.info("Page '{}' handled, {} sub-category(ies) found, {} sub-page(s) found.",
				generateFullPath(response.raw().request().url().pathSegments()),
				subCateTitles.size(),
				subPageTitles.size()
			);
		};
	}

	private static List<String> findAllLinks(Element root)
	{
		LinkedList<String> result = new LinkedList<>();
		if (root == null)
		{
			return result;
		}
		Elements linkElements = root.select("a");
		for (Element linkElement : linkElements)
		{
			String link = linkElement.attr("href");
			if (!link.isEmpty())
			{
				result.add(link.substring(1));
			}
		}
		return result;
	}

	private static void print(String parent, String child)
	{
		Set<String> titleSet = Collections.synchronizedSet(new HashSet<>());
		Set<String> oriSet = result.putIfAbsent(parent, titleSet);
		titleSet = oriSet == null ? titleSet : oriSet;
		titleSet.add(child);
	}

	private static String generateFullPath(@NonNull List<String> paths)
	{
		StringBuilder builder = new StringBuilder();
		for (String path : paths)
		{
			builder.append("/").append(path);
		}
		return builder.toString();
	}
}
