package com.abhishek.smarthome.vendor.auth;

import com.abhishek.smarthome.vendor.Vendor;
import com.abhishek.smarthome.vendor.config.AuthProperties;
import com.abhishek.smarthome.vendor.config.AuthType;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Creates the outbound-auth interceptor for one {@link AuthType}. Adding a new auth scheme means adding an
 * {@link AuthType} constant and one implementation of this interface as a Spring bean; clients are unchanged.
 */
public interface VendorAuthInterceptorFactory {

	AuthType type();

	ClientHttpRequestInterceptor create(Vendor vendor, AuthProperties auth);
}
