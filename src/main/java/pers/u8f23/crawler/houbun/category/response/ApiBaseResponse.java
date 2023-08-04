package pers.u8f23.crawler.houbun.category.response;

import lombok.Getter;
import lombok.Setter;

/**
 * @author 8f23
 * @create 2023/8/4-23:00
 */
@Getter
@Setter
public class ApiBaseResponse<T>
{
	private T query;
}
