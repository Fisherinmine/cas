package org.apereo.cas.support.saml.services.logout;

import org.apereo.cas.configuration.model.support.saml.idp.SamlIdPProperties;
import org.apereo.cas.logout.slo.SingleLogoutMessageCreator;
import org.apereo.cas.logout.slo.SingleLogoutRequest;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.SamlUtils;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.support.saml.services.idp.metadata.cache.SamlRegisteredServiceCachingMetadataResolver;
import org.apereo.cas.support.saml.util.AbstractSaml20ObjectBuilder;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.RandomUtils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.shibboleth.idp.attribute.IdPAttribute;
import net.shibboleth.idp.attribute.StringAttributeValue;
import net.shibboleth.idp.saml.attribute.encoding.impl.SAML2StringNameIDEncoder;
import org.apache.commons.lang3.StringUtils;
import org.jasig.cas.client.authentication.AttributePrincipal;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.opensaml.saml.saml2.core.NameID;

/**
 * This is {@link SamlProfileSingleLogoutMessageCreator}.
 *
 * @author Misagh Moayyed
 * @since 6.0.0
 */
@Slf4j
public class SamlProfileSingleLogoutMessageCreator extends AbstractSaml20ObjectBuilder implements SingleLogoutMessageCreator {

    private static final long serialVersionUID = -5895467960534493675L;

    /**
     * The Services manager.
     */
    protected final transient ServicesManager servicesManager;

    /**
     * The Saml registered service caching metadata resolver.
     */
    protected final transient SamlRegisteredServiceCachingMetadataResolver samlRegisteredServiceCachingMetadataResolver;

    /**
     * SAML idp settings.
     */
    protected final SamlIdPProperties samlIdPProperties;

    public SamlProfileSingleLogoutMessageCreator(final OpenSamlConfigBean configBean,
                                                 final ServicesManager servicesManager,
                                                 final SamlRegisteredServiceCachingMetadataResolver samlRegisteredServiceCachingMetadataResolver,
                                                 final SamlIdPProperties samlIdPProperties) {
        super(configBean);
        this.servicesManager = servicesManager;
        this.samlRegisteredServiceCachingMetadataResolver = samlRegisteredServiceCachingMetadataResolver;
        this.samlIdPProperties = samlIdPProperties;
    }

    @Override
    @SneakyThrows
    public String create(final SingleLogoutRequest request) {
        val id = '_' + String.valueOf(RandomUtils.getNativeInstance().nextLong());
        val issueInstant = DateTime.now(DateTimeZone.UTC).plusSeconds(samlIdPProperties.getResponse().getSkewAllowance());

        val encoder = new SAML2StringNameIDEncoder();

        val samlService = (SamlRegisteredService) request.getRegisteredService();
        encoder.setNameFormat(StringUtils.defaultIfBlank(samlService.getRequiredNameIdFormat(), NameID.UNSPECIFIED));
        encoder.setNameQualifier(samlService.getNameIdQualifier());

        val attribute = new IdPAttribute(AttributePrincipal.class.getName());

        val principalName = request.getTicketGrantingTicket().getAuthentication().getPrincipal().getId();
        LOGGER.debug("Preparing NameID attribute for principal [{}]", principalName);
        attribute.setValues(CollectionUtils.wrap(new StringAttributeValue(principalName)));
        val nameId = encoder.encode(attribute);

        val samlLogoutRequest = newLogoutRequest(id, issueInstant,
            request.getLogoutUrl().toExternalForm(),
            newIssuer(samlIdPProperties.getEntityId()),
            request.getTicketId(),
            nameId);

        val xmlRequest = SamlUtils.transformSamlObject(this.configBean, samlLogoutRequest).toString();
        LOGGER.debug("SAML2 logout request prepared for [{}] is:\n[{}]", samlService, xmlRequest);
        return xmlRequest;
    }
}
