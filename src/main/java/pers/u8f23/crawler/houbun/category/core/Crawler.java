package pers.u8f23.crawler.houbun.category.core;

import com.google.gson.Gson;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import pers.u8f23.crawler.houbun.category.HttpService;
import retrofit2.Response;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * @author 8f23
 * @create 2023/7/2-18:37
 */
@Slf4j
public final class Crawler
{
	private static final int TIMER_COUNTER_INIT_NUM = 10;

	private final ScheduledThreadPoolExecutor parserPool = new ScheduledThreadPoolExecutor(10);
	private int uploadTaskCounter = 0;
	private int finishedTaskCounter = 0;
	private int timerCounter = TIMER_COUNTER_INIT_NUM;
	private final Map<String, Set<String>> result;
	private final String outputFilePath;
	private final Set<String> traversedSet = new HashSet<>();

	public Crawler(@NonNull String rootCateTitle, @NonNull String outputFilePath)
	{
		result = Collections.synchronizedMap(new HashMap<>());
		this.outputFilePath = outputFilePath;
		new Timer().scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				handleTimer();
			}
		}, 0, 1000);
		addFirst(rootCateTitle);
	}

	public void addFirst(String rootCateTitle)
	{
		traverseCate(rootCateTitle, (comicTitle) ->
			traverseCate(comicTitle, (charaTitle) -> print(comicTitle, charaTitle))
		);
	}

	private void traverseCate(String rootTitle, Consumer<String> handleSubPage)
	{
		requestPage(rootTitle, parseCatePage(
			(title) -> traverseCate(title, handleSubPage),
			handleSubPage
		));
	}

	private void handleTaskCreate()
	{
		synchronized (this)
		{
			uploadTaskCounter++;
		}
	}

	private void handleTaskFinish()
	{
		synchronized (this)
		{
			finishedTaskCounter++;
		}
	}

	private void handleTimer()
	{
		int currTask;
		synchronized (this)
		{
			currTask = uploadTaskCounter - finishedTaskCounter;
		}
		if (currTask <= 0)
		{
			timerCounter--;
		}
		else
		{
			timerCounter++;
		}
		if (timerCounter <= 0)
		{
			try (OutputStream outputStream = new FileOutputStream(outputFilePath))
			{
				outputStream.write(new Gson().toJson(result).getBytes(StandardCharsets.UTF_8));
			}
			catch (Exception e)
			{
				log.error("Failed to print result", e);
			}
			log.info("Succeed to finish.");
			System.exit(0);
		}
		log.info("Exist task: {}", currTask);
	}

	private void requestPage(
		@NonNull String title, @NonNull Consumer<Response<ResponseBody>> consumer)
	{
		if (title.startsWith("/"))
		{
			title = title.substring(1);
		}
		if (!title.startsWith("Category:"))
		{
			title = "Category:" + title;
		}
		synchronized (traversedSet)
		{
			if (traversedSet.contains(title))
			{
				return;
			}
			traversedSet.add(title);
		}
		log.info("Request page: '{}'", title);
		HttpService.getInstance()
			.get(title)
			.subscribeOn(Schedulers.single())
			.observeOn(Schedulers.from(parserPool))
			.subscribe(new TaskObserver<>(
				consumer,
				this::handleTaskCreate,
				this::handleTaskFinish
			));
	}

	private Consumer<Response<ResponseBody>> parseCatePage(
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

	private List<String> findAllLinks(Element root)
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
				try
				{
					link = URLDecoder.decode(link, "UTF-8");
				}
				catch (Exception ignored)
				{
				}
				// remove char '/'
				result.add(link.substring(1));
			}
		}
		return result;
	}

	private void print(String parent, String child)
	{
		Set<String> titleSet = Collections.synchronizedSet(new HashSet<>());
		Set<String> oriSet = result.putIfAbsent(parent, titleSet);
		titleSet = oriSet == null ? titleSet : oriSet;
		titleSet.add(child);
	}

	private String generateFullPath(@NonNull List<String> paths)
	{
		StringBuilder builder = new StringBuilder();
		for (String path : paths)
		{
			builder.append("/").append(path);
		}
		return builder.toString();
	}
}









