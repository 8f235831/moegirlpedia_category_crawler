package pers.u8f23.crawler.houbun.category;

import io.reactivex.rxjava3.core.Single;
import okhttp3.ResponseBody;
import pers.u8f23.crawler.houbun.category.response.ApiBaseResponse;
import pers.u8f23.crawler.houbun.category.response.Query;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Url;

/**
 * @author 8f23
 * @create 2023/7/2-17:09
 */
public interface HomeSiteService
{
	@GET ("/index.php")
	Single<Response<ResponseBody>> get(@retrofit2.http.Query ("title") String pageTitle);
	@GET
	Single<Response<ResponseBody>> getUrl(@Url String url);
	@GET ("/api.php?action=query&prop=categories&format=json")
	Single<Response<ApiBaseResponse<Query>>> getCategories(@retrofit2.http.Query ("titles") String pageTitle);

	static HomeSiteService getInstance()
	{
		HomeSiteService localInstance = HttpServiceHolder.instance;
		if (localInstance != null)
		{
			return localInstance;
		}
		HttpServiceHolder.instance = localInstance = HttpUtils.buildHomeSiteService(HomeSiteService.class);
		return localInstance;
	}

	class HttpServiceHolder
	{
		private static HomeSiteService instance;
	}
}

