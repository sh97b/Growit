package com.growit.dot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ErrorCode;
import com.growit.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 도트 그림 시작 요청(사진으로 만든 격자 포함)의 본문 크기를 제한한다. 200칸 격자는 몇 KB면 충분하므로, 사진 같은 큰 데이터가
 * 이 주소로 들어오지 못하게 한다. 길이를 미리 알 수 없는 요청은 읽는 도중에 한도를 넘으면 읽기를 끊는다.
 */
@Component
public class DotRequestSizeFilter extends OncePerRequestFilter {

	static final int MAX_BODY_BYTES = 16 * 1024;
	private static final String PATH = "/api/dots/goals";

	private final ObjectMapper objectMapper;

	public DotRequestSizeFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equals(request.getMethod()) && PATH.equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > MAX_BODY_BYTES) {
			response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			objectMapper.writeValue(response.getWriter(),
					new ErrorResponse(ErrorCode.PAYLOAD_TOO_LARGE.name(), "요청이 너무 커요"));
			return;
		}
		chain.doFilter(new LimitedRequest(request), response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private LimitedRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			return new LimitedStream(super.getInputStream());
		}

		@Override
		public BufferedReader getReader() throws IOException {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}

	private static final class LimitedStream extends ServletInputStream {

		private final ServletInputStream delegate;
		private int count;

		private LimitedStream(ServletInputStream delegate) {
			this.delegate = delegate;
		}

		@Override
		public int read() throws IOException {
			int value = delegate.read();
			if (value != -1 && ++count > MAX_BODY_BYTES) {
				throw new IOException("요청 본문이 너무 커요");
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int read = delegate.read(buffer, offset, length);
			if (read > 0) {
				count += read;
				if (count > MAX_BODY_BYTES) {
					throw new IOException("요청 본문이 너무 커요");
				}
			}
			return read;
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			delegate.setReadListener(readListener);
		}
	}
}
