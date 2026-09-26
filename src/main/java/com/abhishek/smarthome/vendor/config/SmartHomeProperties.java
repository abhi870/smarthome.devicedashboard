package com.abhishek.smarthome.vendor.config;

import com.abhishek.smarthome.vendor.Vendor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Root {@code smarthome.*} configuration. Vendor keys must match a {@link Vendor} constant
 * (e.g. {@code smarthome.vendors.samsung}); unknown keys fail startup.
 */
@Validated
@ConfigurationProperties("smarthome")
public record SmartHomeProperties(@NotEmpty Map<Vendor, @Valid VendorProperties> vendors) {
}
