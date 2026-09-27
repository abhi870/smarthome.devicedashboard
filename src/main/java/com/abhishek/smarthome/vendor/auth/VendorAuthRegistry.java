package com.abhishek.smarthome.vendor.auth;

import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.config.AuthType;
import com.abhishek.smarthome.vendor.config.VendorConfigProvider;
import com.abhishek.smarthome.vendor.config.VendorNotConfiguredException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;

/**
 * Builds one auth interceptor per configured vendor at startup, using the factory registered for the vendor's
 * {@link AuthType}. Fails fast when a configured auth type has no factory or two factories claim the same type.
 */
@Component
public class VendorAuthRegistry {

	private final Map<VendorCode, ClientHttpRequestInterceptor> interceptors = new EnumMap<>(VendorCode.class);

	public VendorAuthRegistry(List<VendorAuthInterceptorFactory> factories, VendorConfigProvider config) {
		Map<AuthType, VendorAuthInterceptorFactory> byType = indexByType(factories);
		for (VendorCode vendor : config.configuredVendors()) {
			AuthType type = config.get(vendor).getAuth().getType();
			VendorAuthInterceptorFactory factory = byType.get(type);
			if (factory == null) {
				throw new IllegalStateException("No auth factory for type %s (vendor %s); available: %s"
						.formatted(type, vendor, byType.keySet()));
			}
			interceptors.put(vendor, factory.create(vendor, config.get(vendor).getAuth()));
		}
	}

	/**
	 * @throws VendorNotConfiguredException if the vendor has no configuration
	 */
	public ClientHttpRequestInterceptor interceptorFor(VendorCode vendor) {
		ClientHttpRequestInterceptor interceptor = interceptors.get(vendor);
		if (interceptor == null) {
			throw new VendorNotConfiguredException(vendor);
		}
		return interceptor;
	}

	private static Map<AuthType, VendorAuthInterceptorFactory> indexByType(List<VendorAuthInterceptorFactory> factories) {
		Map<AuthType, VendorAuthInterceptorFactory> byType = new EnumMap<>(AuthType.class);
		for (VendorAuthInterceptorFactory factory : factories) {
			VendorAuthInterceptorFactory previous = byType.put(factory.type(), factory);
			if (previous != null) {
				throw new IllegalStateException("Duplicate auth factories for type %s: %s and %s"
						.formatted(factory.type(), previous.getClass().getSimpleName(),
								factory.getClass().getSimpleName()));
			}
		}
		return byType;
	}
}
