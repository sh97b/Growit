package com.growit.dot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DotRequestSizeFilterTest {

	private final DotRequestSizeFilter filter = new DotRequestSizeFilter(new ObjectMapper());

	private static MockHttpServletRequest post(String path, byte[] body) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
		request.setRequestURI(path);
		request.setContent(body);
		return request;
	}

	@Test
	void smallBody_passesThrough() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(post("/api/dots/goals", new byte[1000]), response, chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void oversizedContentLength_is413_andNeverReachesTheController() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(post("/api/dots/goals", new byte[DotRequestSizeFilter.MAX_BODY_BYTES + 1]), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(413);
		assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
	}

	@Test
	void unknownLengthBody_isCutOffWhileReading() throws Exception {
		MockHttpServletRequest request = post("/api/dots/goals", new byte[DotRequestSizeFilter.MAX_BODY_BYTES + 100]);
		request.addHeader("Transfer-Encoding", "chunked");
		HttpServletRequest[] seen = new HttpServletRequest[1];
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/dots/goals") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}

			@Override
			public jakarta.servlet.ServletInputStream getInputStream() {
				return request.getInputStream();
			}

			@Override
			public String getRequestURI() {
				return "/api/dots/goals";
			}
		}, new MockHttpServletResponse(), chain);
		seen[0] = (HttpServletRequest) chain.getRequest();

		assertThat(seen[0]).isNotNull();
		assertThatThrownBy(() -> seen[0].getInputStream().readAllBytes()).isInstanceOf(IOException.class);
	}

	@Test
	void otherPathsAndMethods_areNotFiltered() throws Exception {
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(post("/api/records", new byte[DotRequestSizeFilter.MAX_BODY_BYTES + 1]),
				new MockHttpServletResponse(), chain);
		assertThat(chain.getRequest()).isNotNull();

		MockFilterChain chain2 = new MockFilterChain();
		MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/dots/goals");
		get.setRequestURI("/api/dots/goals");
		filter.doFilter(get, new MockHttpServletResponse(), chain2);
		assertThat(chain2.getRequest()).isNotNull();
	}
}
