package pers.u8f23.crawler.houbun.category;

import lombok.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author 8f23
 * @create 2023/7/2-17:09
 */
public class HttpUtils
{
	public static final int HOME_SITE_TIME_OUT = 3;
	public static final int MIRROR_SITE_TIME_OUT = 5 * 60;
	public static final String HOME_SITE_BASE_URL = "https://zh.moegirl.org.cn";
	public static final String MIRROR_SITE_BASE_URL = "https://moegirl.uk";

	private static final Retrofit HOME_SITE_SERVICE_CREATOR;
	private static final Retrofit MIRROR_SITE_SERVICE_CREATOR;

	static
	{
		// 日志拦截器
		HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
		logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

		OkHttpClient homeSiteClient = new OkHttpClient.Builder()
			.addInterceptor(logging)
			.connectTimeout(HOME_SITE_TIME_OUT, TimeUnit.SECONDS)
			.build();
		HOME_SITE_SERVICE_CREATOR = new Retrofit.Builder()
			.addConverterFactory(GsonConverterFactory.create())
			.baseUrl(HOME_SITE_BASE_URL)
			.addCallAdapterFactory(RxJava3CallAdapterFactory.create())
			.client(homeSiteClient)
			.build();

		OkHttpClient mirrorSiteClient = new OkHttpClient.Builder()
			.addInterceptor(logging)
			.connectTimeout(MIRROR_SITE_TIME_OUT, TimeUnit.SECONDS)
			.build();
		MIRROR_SITE_SERVICE_CREATOR = new Retrofit.Builder()
			.addConverterFactory(GsonConverterFactory.create())
			.baseUrl(MIRROR_SITE_BASE_URL)
			.addCallAdapterFactory(RxJava3CallAdapterFactory.create())
			.client(mirrorSiteClient)
			.build();
	}

	public static <S> S buildHomeSiteService(Class<S> clazz)
	{
		return HOME_SITE_SERVICE_CREATOR.create(clazz);
	}

	public static <S> S buildMirrorSiteService(Class<S> clazz)
	{
		return MIRROR_SITE_SERVICE_CREATOR.create(clazz);
	}

	@NonNull
	public static Set<String> parseRawHtmlCategoryPage(Response<ResponseBody> rawResponse)
	{
		Set<String> resultSet = new HashSet<>();
		if (rawResponse == null)
		{
			return resultSet;
		}
		String content = null;
		try (ResponseBody body = rawResponse.body())
		{
			if (body == null)
			{
				return resultSet;
			}
			content = body.string();
		}
		catch (IOException ignored)
		{
		}
		if (content == null)
		{
			return resultSet;
		}
		Document document = Jsoup.parse(content);
		resultSet.addAll(findAllLinks(document.select("#mw-subcategories").first()));
		resultSet.addAll(findAllLinks(document.select("#mw-pages").first()));
		return resultSet;
	}

	public static <T> T parseResponse(Response<T> rawResponse)
	{
		T body = Objects.requireNonNull(rawResponse).body();
		return Objects.requireNonNull(body);
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
}
