package com.abhishek.smarthome.vendor.auth;

import com.abhishek.smarthome.enums.AuthType;
import com.abhishek.smarthome.enums.VendorCode;
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

	private final Map<VendorCode, ClientHttpRequestInterceptor> interceptorsByVendor = new EnumMap<>(VendorCode.class);

	public VendorAuthRegistry(List<VendorAuthInterceptorFactory> authInterceptorFactories, VendorConfigProvider vendorConfigProvider) {
		Map<AuthType, VendorAuthInterceptorFactory> byType = indexByType(authInterceptorFactories);

		for (VendorCode vendor : vendorConfigProvider.configuredVendors()) {
			AuthType type = vendorConfigProvider.get(vendor).getAuth().getType();

			VendorAuthInterceptorFactory factory = byType.get(type);
			if (factory == null) {
				throw new IllegalStateException("No auth factory for type %s (vendor %s); available: %s"
						.formatted(type, vendor, byType.keySet()));
			}
			interceptorsByVendor.put(vendor, factory.create(vendor, vendorConfigProvider.get(vendor).getAuth()));
		}
	}

	/**
	 * @throws VendorNotConfiguredException if the vendor has no configuration
	 */
	public ClientHttpRequestInterceptor interceptorFor(VendorCode vendor) {
		ClientHttpRequestInterceptor interceptor = interceptorsByVendor.get(vendor);
		if (interceptor == null) {
			throw new VendorNotConfiguredException(vendor);
		}
		return interceptor;
	}

	private static Map<AuthType, VendorAuthInterceptorFactory> indexByType(List<VendorAuthInterceptorFactory> authInterceptorFactories) {
		Map<AuthType, VendorAuthInterceptorFactory> byType = new EnumMap<>(AuthType.class);

		for (VendorAuthInterceptorFactory factory : authInterceptorFactories) {
			AuthType authType = factory.getAuthType();
			if (byType.containsKey(authType)) {
				throw new IllegalStateException("Duplicate auth factories for type %s: %s and %s".formatted(authType,
					byType.get(authType).getClass().getSimpleName(), factory.getClass().getSimpleName()));
			}
			byType.put(authType, factory);
		}
		return byType;
	}
}
