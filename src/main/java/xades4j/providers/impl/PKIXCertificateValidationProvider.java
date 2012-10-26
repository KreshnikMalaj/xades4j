/*
 * XAdES4j - A Java library for generation and verification of XAdES signatures.
 * Copyright (C) 2010 Luis Goncalves.
 *
 * XAdES4j is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or any later version.
 *
 * XAdES4j is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with XAdES4j. If not, see <http://www.gnu.org/licenses/>.
 */
package xades4j.providers.impl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLSelector;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

import xades4j.providers.CannotBuildCertificationPathException;
import xades4j.providers.CannotSelectCertificateException;
import xades4j.providers.CertificateValidationException;
import xades4j.providers.CertificateValidationProvider;
import xades4j.providers.ValidationData;
import xades4j.verification.UnexpectedJCAException;

/**
 * Implementation of {@code CertificateValidationProvider} using a PKIX {@code CertPathBuilder}.
 * <p>
 * Since the Java's PKIX API doesn't allow to access the CRLs used in the certification
 * path validation, this is manually done. There has to be a CRL for each issuer
 * in the path which is valid at the moment of validation (signature and date).
 * @author Luís
 */
public class PKIXCertificateValidationProvider implements CertificateValidationProvider
{
    private static final int DEFAULT_MAX_PATH_LENGTH = 6;

    private final KeyStore trustAnchors;
    private final boolean revocationEnabled;
    private final int maxPathLength;
    private       CertStore[] intermCertsAndCrls;
    private final CertPathBuilder certPathBuilder;
    private final String signatureProvider;

    /**
     * Initializes a new instance that uses the specified JCE providers for CertPathBuilder
     * and Signature.
     * @param trustAnchors the keystore with the trust-anchors ({@code TrustedCertificateEntry})
     * @param revocationEnabled whether revocation is enabled
     * @param maxPathLength the maximum length of the certification paths
     * @param certPathBuilderProvider the CertPathBuilder provider
     * @param signatureProvider the Signature provider
     * @param intermCertsAndCrls a set of {@code CertStore}s that contain certificates to be
     *      used in the construction of the certification path. May contain CRLs to be used
     *      if revocation is enabled
     * @see xades4j.utils.FileSystemDirectoryCertStore
     * @throws NoSuchAlgorithmException if there is no provider for PKIX CertPathBuilder
     */
    public PKIXCertificateValidationProvider(
            KeyStore trustAnchors,
            boolean revocationEnabled,
            int maxPathLength,
            String certPathBuilderProvider,
            String signatureProvider,
            CertStore... intermCertsAndCrls) throws NoSuchAlgorithmException, NoSuchProviderException
    {
        if (null == trustAnchors)
        {
            throw new NullPointerException("Trust anchors cannot be null");
        }

        this.trustAnchors = trustAnchors;
        this.revocationEnabled = revocationEnabled;
        this.maxPathLength = maxPathLength;
        this.certPathBuilder = certPathBuilderProvider == null ? CertPathBuilder.getInstance("PKIX") : CertPathBuilder.getInstance("PKIX", certPathBuilderProvider);
        this.signatureProvider = signatureProvider;
        this.intermCertsAndCrls = intermCertsAndCrls;
    }

    /**
     * Initializes a new instance that uses the specified JCE providers for CertPathBuilder
     * and Signature.
     * @param trustAnchors the keystore with the trust-anchors ({@code TrustedCertificateEntry})
     * @param revocationEnabled whether revocation is enabled
     * @param certPathBuilderProvider the CertPathBuilder provider
     * @param signatureProvider the Signature provider
     * @param intermCertsAndCrls a set of {@code CertStore}s that contain certificates to be
     *      used in the construction of the certification path. May contain CRLs to be used
     *      if revocation is enabled
     * @see xades4j.utils.FileSystemDirectoryCertStore
     * @throws NoSuchAlgorithmException if there is no provider for PKIX CertPathBuilder
     */
    public PKIXCertificateValidationProvider(
            KeyStore trustAnchors,
            boolean revocationEnabled,
            String certPathBuilderProvider,
            String signatureProvider,
            CertStore... intermCertsAndCrls) throws NoSuchAlgorithmException, NoSuchProviderException
    {
        this(trustAnchors, revocationEnabled, DEFAULT_MAX_PATH_LENGTH, certPathBuilderProvider, signatureProvider, intermCertsAndCrls);
    }

    /**
     * Initializes a new instance that uses the specified JCE provider for both
     * CertPathBuilder and Signature.
     * @param trustAnchors the keystore with the trust-anchors ({@code TrustedCertificateEntry})
     * @param revocationEnabled whether revocation is enabled
     * @param maxPathLength the maximum length of the certification paths
     * @param jceProvider the CertPathBuilder and Signature provider
     * @param intermCertsAndCrls a set of {@code CertStore}s that contain certificates to be
     *      used in the construction of the certification path. May contain CRLs to be used
     *      if revocation is enabled
     * @see xades4j.utils.FileSystemDirectoryCertStore
     * @throws NoSuchAlgorithmException if there is no provider for PKIX CertPathBuilder
     */
    public PKIXCertificateValidationProvider(
            KeyStore trustAnchors,
            boolean revocationEnabled,
            int maxPathLength,
            String jceProvider,
            CertStore... intermCertsAndCrls) throws NoSuchAlgorithmException, NoSuchProviderException
    {
        this(trustAnchors, revocationEnabled, maxPathLength, jceProvider, jceProvider, intermCertsAndCrls);
    }

    /**
     * Initializes a new instance that uses the specified JCE provider for both
     * CertPathBuilder and Signature.
     * @param trustAnchors the keystore with the trust-anchors ({@code TrustedCertificateEntry})
     * @param revocationEnabled whether revocation is enabled
     * @param jceProvider the CertPathBuilder and Signature provider
     * @param intermCertsAndCrls a set of {@code CertStore}s that contain certificates to be
     *      used in the construction of the certification path. May contain CRLs to be used
     *      if revocation is enabled
     * @see xades4j.utils.FileSystemDirectoryCertStore
     * @throws NoSuchAlgorithmException if there is no provider for PKIX CertPathBuilder
     */
    public PKIXCertificateValidationProvider(
            KeyStore trustAnchors,
            boolean revocationEnabled,
            String jceProvider,
            CertStore... intermCertsAndCrls) throws NoSuchAlgorithmException, NoSuchProviderException
    {
        this(trustAnchors, revocationEnabled, DEFAULT_MAX_PATH_LENGTH, jceProvider, intermCertsAndCrls);
    }

    /**
     * Initializes a new instance without specifying the JCE providers for CertPathBuilder
     * and Signature.
     * @param trustAnchors the keystore with the trust-anchors ({@code TrustedCertificateEntry})
     * @param revocationEnabled whether revocation is enabled
     * @param maxPathLength the maximum length of the certification paths
     * @param intermCertsAndCrls a set of {@code CertStore}s that contain certificates to be
     *      used in the construction of the certification path. May contain CRLs to be used
     *      if revocation is enabled
     * @see xades4j.utils.FileSystemDirectoryCertStore
     * @throws NoSuchAlgorithmException if there is no provider for PKIX CertPathBuilder
     */
    public PKIXCertificateValidationProvider(
            KeyStore trustAnchors,
            boolean revocationEnabled,
            int maxPathLength,
            CertStore... intermCertsAndCrls) throws NoSuchAlgorithmException, NoSuchProviderException
    {
        this(trustAnchors, revocationEnabled, maxPathLength, null, null, intermCertsAndCrls);
    }

    /**
     * Initializes a new instance without specifying the JCE providers for CertPathBuilder
     * and Signature.
     * @param trustAnchors the keystore with the trust-anchors ({@code TrustedCertificateEntry})
     * @param revocationEnabled whether revocation is enabled
     * @param intermCertsAndCrls a set of {@code CertStore}s that contain certificates to be
     *      used in the construction of the certification path. May contain CRLs to be used
     *      if revocation is enabled
     * @see xades4j.utils.FileSystemDirectoryCertStore
     * @throws NoSuchAlgorithmException if there is no provider for PKIX CertPathBuilder
     */
    public PKIXCertificateValidationProvider(
            KeyStore trustAnchors,
            boolean revocationEnabled,
            CertStore... intermCertsAndCrls) throws NoSuchAlgorithmException, NoSuchProviderException
    {
        this(trustAnchors, revocationEnabled, DEFAULT_MAX_PATH_LENGTH, null, null, intermCertsAndCrls);
    }

    @Override
    public ValidationData validate(
            X509CertSelector certSelector,
            Date validationDate,
            Collection<X509Certificate> otherCerts) throws CertificateValidationException, UnexpectedJCAException
    {
        PKIXBuilderParameters builderParams;
        try
        {
            builderParams = new PKIXBuilderParameters(trustAnchors, certSelector);
        } catch (KeyStoreException ex)
        {
            throw new CannotBuildCertificationPathException(certSelector, "Trust anchors KeyStore is not initialized", ex);
        } catch (InvalidAlgorithmParameterException ex)
        {
            throw new CannotBuildCertificationPathException(certSelector, "Trust anchors KeyStore has no trusted certificate entries", ex);
        }

        PKIXCertPathBuilderResult builderRes;
        try
        {
            // Certificates to be used to build the certification path.
            // - The other certificates from the signature (e.g. from KeyInfo).
            if (otherCerts != null)
            {
                CollectionCertStoreParameters ccsp = new CollectionCertStoreParameters(otherCerts);
                CertStore othersCertStore = CertStore.getInstance("Collection", ccsp);
                builderParams.addCertStore(othersCertStore);
            }
            // - The external certificates/CRLs.
            for (int i = 0; i < intermCertsAndCrls.length; i++)
            {
                builderParams.addCertStore(intermCertsAndCrls[i]);
            }

            builderParams.setRevocationEnabled(revocationEnabled);
            builderParams.setMaxPathLength(maxPathLength);
            builderParams.setDate(validationDate);
            builderParams.setSigProvider(this.signatureProvider);

            builderRes = (PKIXCertPathBuilderResult) certPathBuilder.build(builderParams);
        }
        catch (CertPathBuilderException ex)
        {
            throw new CannotBuildCertificationPathException(certSelector, ex.getMessage(), ex);
        } catch (InvalidAlgorithmParameterException ex)
        {
            // SHOULD NOT be thrown due to wrong type of parameters.
            // Seems to be thrown when the CertSelector (in builderParams) criteria
            // cannot be applied.
            throw new CannotSelectCertificateException(certSelector, ex);
        } catch (NoSuchAlgorithmException ex)
        {
            // SHOULD NOT be thrown.
            throw new UnexpectedJCAException("No provider for Collection CertStore", ex);
        }

        // The cert path returned by the builder ends in a certificate issued by
        // the trust anchor. However, the complete path may be needed for property
        // verification.
        List<X509Certificate> certPath = (List<X509Certificate>) builderRes.getCertPath().getCertificates();
        // - Create a new list since the previous is immutable.
        certPath = new ArrayList<X509Certificate>(certPath);
        // - Add the trust anchor certificate.
        certPath.add(builderRes.getTrustAnchor().getTrustedCert());

        if (revocationEnabled)
        {
            return new ValidationData(certPath, getCRLsForCertPath(certPath, validationDate));
        }
        return new ValidationData(certPath);
    }

    private Collection<X509CRL> getCRLsForCertPath(
            List<X509Certificate> certPath,
            Date validationDate) throws CertificateValidationException
    {
        // Map the issuers certificates in the chain. This is used to know the issuers
        // and later to verify the signatures in the CRLs.
        Map<X500Principal, X509Certificate> issuersCerts = new HashMap<X500Principal, X509Certificate>(certPath.size() - 1);
        for (int i = 0; i < certPath.size() - 1; i++)
        {
            // The issuer of one certificate is the subject of the following one.
            issuersCerts.put(certPath.get(i).getIssuerX500Principal(), certPath.get(i + 1));
        }

        // Select all the CRLs from the issuers involved in the certification path
        // that are valid at the moment.
        X509CRLSelector crlSelector = new X509CRLSelector();
        for (X500Principal issuer : issuersCerts.keySet())
        {
            // - "The issuer distinguished name in the X509CRL must match at least
            //   one of the specified distinguished names."
            crlSelector.addIssuer(issuer);
        }
        // - "The specified date must be equal to or later than the value of the
        //   thisUpdate component of the X509CRL and earlier than the value of the
        //   nextUpdate component."
        crlSelector.setDateAndTime(validationDate);

        Set<X509CRL> crls = new HashSet<X509CRL>();
        try
        {
            // Get the CRLs on each CertStore.
            for (int i = 0; i < intermCertsAndCrls.length; i++)
            {
                Collection storeCRLs = intermCertsAndCrls[i].getCRLs(crlSelector);
                crls.addAll(Collections.checkedCollection(storeCRLs, X509CRL.class));

            }
        } catch (CertStoreException ex)
        {
            throw new CertificateValidationException(null, "Cannot get CRLs", ex);
        }

        // Verify the CRLs' signatures. The issuers' certificates were validated
        // as part of the cert path creation.
        for (X509CRL crl : crls)
        {
            try
            {
                X509Certificate crlIssuerCert = issuersCerts.get(crl.getIssuerX500Principal());
                if (null == this.signatureProvider)
                {
                    crl.verify(crlIssuerCert.getPublicKey());
                }
                else
                {
                    crl.verify(crlIssuerCert.getPublicKey(), this.signatureProvider);
                }
            }
            catch (Exception ex)
            {
                throw new CertificateValidationException(null, "Invalid CRL signature from " + crl.getIssuerX500Principal().getName(), ex);
            }
        }
        return crls;
    }

    @Override
    public void addCRLs(Collection<X509CRL> crls, Date now)
    {
        Collection<X509CRL> validCRLs = new ArrayList<X509CRL>();

        for(X509CRL crl : crls)
        {
            // check if it's not CRL from "future"
            if (crl.getThisUpdate().getTime() > now.getTime())
                continue;

            // TODO check algorithms used in CRL

            X509CertSelector certSel = new X509CertSelector();
            certSel.setSubject(crl.getIssuerX500Principal());

            PKIXBuilderParameters params;
            try
            {
                params = new PKIXBuilderParameters(trustAnchors, certSel);
            } catch (Exception e)
            {
                // parameters are invalid, ignore CRL
                continue;
            }
            params.setDate(now);
            params.setRevocationEnabled(false);
            for (int i=0; i < intermCertsAndCrls.length; i++)
            {
                params.addCertStore(intermCertsAndCrls[i]);
            }

            PKIXCertPathBuilderResult res;
            try
            {
                res = (PKIXCertPathBuilderResult) this.certPathBuilder.build(params);
            } catch (Exception ex)
            {
                // CRL is invalid, ignore
                continue;
            }

            List certs = new ArrayList(res.getCertPath().getCertificates());
            // TODO check algorithms used in this cert path
            PublicKey crlSigningKey;
            if (certs.size() != 0)
                crlSigningKey = ((Certificate)certs.get(0)).getPublicKey();
            else
                crlSigningKey = res.getTrustAnchor().getCAPublicKey();
            if (crlSigningKey == null)
                crlSigningKey = res.getTrustAnchor().getTrustedCert().getPublicKey();

            try
            {
                crl.verify(crlSigningKey);
            } catch (Exception ex)
            {
                // invalid CRL, ignore
                continue;
            }

            validCRLs.add(crl);
        }

        // don't create empty CertStores
        if (validCRLs.size() == 0)
            return;

        CollectionCertStoreParameters ccsp = new CollectionCertStoreParameters(validCRLs);
        CertStore crlsCertStore;
        try
        {
            crlsCertStore = CertStore.getInstance("Collection", ccsp);
        } catch (Exception e)
        {
            throw new RuntimeException("General crypto failure", e);
        }

        CertStore[] newIntermCertsAndCrls;
        newIntermCertsAndCrls = Arrays.copyOf(intermCertsAndCrls, intermCertsAndCrls.length + 1);
        newIntermCertsAndCrls[intermCertsAndCrls.length] = crlsCertStore;

        intermCertsAndCrls = newIntermCertsAndCrls;
    }

    @Override
    public void addCertificates(Collection<X509Certificate> otherCerts, Date now)
    {
        Collection<X509Certificate> validCerts = new ArrayList<X509Certificate>();

        /*
         * To validate certificates we need to have all issuer certificates.
         * Needed issuer certificates can be among otherCerts.
         *
         * To work around this problem, we include otherCerts in PKIXBuilderParameters
         * but add them to intermCertsAndCrls only if they validate successfully.
         */
        CollectionCertStoreParameters ccsp = new CollectionCertStoreParameters(otherCerts);
        CertStore otherCertsCertStore;
        try
        {
            otherCertsCertStore = CertStore.getInstance("Collection", ccsp);
        } catch (Exception ex)
        {
            throw new RuntimeException("General crypto failure", ex);
        }

        // find good certificates
        for (X509Certificate cert : otherCerts)
        {
            // TODO check algorithms used in certificate creation

            // TODO iff certificate matches entry in TSL, add them to trustAnchors

            X509CertSelector certSel = new X509CertSelector();
            certSel.setCertificate(cert);

            PKIXBuilderParameters params;
            try {
                params = new PKIXBuilderParameters(trustAnchors, certSel);
            } catch (Exception e)
            {
                // parameters are invalid, ignore certificate
                continue;
            }
            params.setDate(now);
            params.setRevocationEnabled(false);
            for (int i=0; i < intermCertsAndCrls.length; i++)
            {
                params.addCertStore(intermCertsAndCrls[i]);
            }
            params.addCertStore(otherCertsCertStore);

            PKIXCertPathBuilderResult res;
            try
            {
                res = (PKIXCertPathBuilderResult)this.certPathBuilder.build(params);
            } catch (Exception ex)
            {
                // certificate or certificates invalid, ignore
                continue;
            }

            List certs = res.getCertPath().getCertificates();
            if (certs.size() == 0) // cert is a trustAnchor, we can ignore it
                continue;

            validCerts.add(cert);
        }

        ccsp = new CollectionCertStoreParameters(validCerts);
        CertStore validCertCertStore;
        try
        {
            validCertCertStore = CertStore.getInstance("Collection", ccsp);
        } catch (Exception e)
        {
            throw new RuntimeException("General crypto failure", e);
        }

        CertStore[] newIntermCertsAndCrls;
        newIntermCertsAndCrls = Arrays.copyOf(intermCertsAndCrls, intermCertsAndCrls.length + 1);
        newIntermCertsAndCrls[intermCertsAndCrls.length] = validCertCertStore;

        intermCertsAndCrls = newIntermCertsAndCrls;
    }
}
