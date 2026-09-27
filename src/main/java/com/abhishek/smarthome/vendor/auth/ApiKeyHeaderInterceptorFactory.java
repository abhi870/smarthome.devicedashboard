package com.abhishek.smarthome.vendor.auth;

import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.config.AuthProperties;
import com.abhishek.smarthome.vendor.config.AuthType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;

/** {@link AuthType#API_KEY_HEADER}: header {@code name} = {@code prefix + apiKey} (prefix optional). */
@Component
class ApiKeyHeaderInterceptorFactory implements VendorAuthInterceptorFactory {

	@Override
	public AuthType type() {
		return AuthType.API_KEY_HEADER;
	}

	@Override
	public ClientHttpRequestInterceptor create(VendorCode vendor, AuthProperties auth) {
		String prefix = auth.getPrefix() == null ? "" : auth.getPrefix();
		return new ApiKeyHeaderInterceptor(auth.getName(), prefix + auth.getApiKey());
	}
}
