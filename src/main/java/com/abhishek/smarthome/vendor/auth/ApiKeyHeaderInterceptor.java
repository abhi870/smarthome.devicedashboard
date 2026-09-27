package com.abhishek.smarthome.vendor.auth;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Adds the vendor API key to every outgoing request as a header, replacing any existing value. */
@RequiredArgsConstructor
final class ApiKeyHeaderInterceptor implements ClientHttpRequestInterceptor {

	private final String headerName;
	private final String headerValue;

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		request.getHeaders().set(headerName, headerValue);
		return execution.execute(request, body);
	}

	@Override
	public String toString() {
		return "ApiKeyHeaderInterceptor[header=" + headerName + ", value=****]";
	}
}
