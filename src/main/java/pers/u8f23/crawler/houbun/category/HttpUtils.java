package pers.u8f23.crawler.houbun.category;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author 8f23
 * @create 2023/7/2-17:09
 */
public class HttpUtils
{
	/**
	 * 缓存大小，512M。
	 */
	public static final int MAX_CACHE_SIZE = 512 * 1024 * 1024;
	/**
	 * 请求10秒则超时。
	 */
	public static final int TIME_OUT = 10 * 1000;
	public static final String BASE_URL = "https://zh.moegirl.org.cn";

	private static final Retrofit SERVICE_CREATOR;

	static
	{
		// 日志拦截器
		HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
		logging.setLevel(HttpLoggingInterceptor.Level.BODY);

		// 设置缓存路径，内置存储。
		File httpCacheDirectory = new File("./.cache", "responses");
		// 外部存储
		Cache cache = new Cache(httpCacheDirectory, MAX_CACHE_SIZE);

		OkHttpClient client = new OkHttpClient.Builder()
			.cache(cache)
			.addInterceptor(logging)
			.connectTimeout(TIME_OUT, TimeUnit.SECONDS)
			.retryOnConnectionFailure(true)
			.build();

		SERVICE_CREATOR = new Retrofit.Builder()
			.addConverterFactory(GsonConverterFactory.create())
			.baseUrl(BASE_URL)
			.addCallAdapterFactory(RxJava3CallAdapterFactory.create())
			.client(client)
			.build();
	}

	/**
	 * 直接请求内容，内容不会置入缓存。离线不可用。
	 *
	 * @param clazz Retrofit接口类，应继承自<code>BaseHttpService</code> 。
	 * @return 接口实例。
	 */
	public static <S> S buildService(Class<S> clazz)
	{
		return SERVICE_CREATOR.create(clazz);
	}

}
