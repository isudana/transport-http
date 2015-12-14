package org.wso2.carbon.transport.http.netty.internal;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.wso2.carbon.kernel.startupresolver.CapabilityProvider;
import org.wso2.carbon.kernel.transports.CarbonTransport;
import org.wso2.carbon.transport.http.netty.internal.config.YAMLTransportConfigurationBuilder;

/**
 * Component which registers the CarbonTransport capability information.
 */
@Component(
        name = "org.wso2.carbon.transport.http.netty.internal.TransportServiceCapabilityProvider",
        immediate = true,
        service = CapabilityProvider.class,
        property = "capability-name=org.wso2.carbon.kernel.transports.CarbonTransport"
)
public class TransportServiceCapabilityProvider implements CapabilityProvider {

    @Activate
    protected void start(BundleContext bundleContext) {

    }

    public String getName() {
        return CarbonTransport.class.getName();
    }

    @Override
    public int getCount() {
        return YAMLTransportConfigurationBuilder.build().getListenerConfigurations().size();
    }
}