/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ws.security.saml.ext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.saml.SAMLKeyInfo;
import org.apache.ws.security.saml.SAMLUtil;
import org.apache.ws.security.saml.ext.builder.SAML1ComponentBuilder;
import org.apache.ws.security.saml.ext.builder.SAML2ComponentBuilder;

import org.apache.ws.security.util.DOM2Writer;
import org.apache.ws.security.util.UUIDGenerator;

import org.opensaml.common.SAMLVersion;
import org.opensaml.common.SignableSAMLObject;
import org.opensaml.saml1.core.AuthenticationStatement;
import org.opensaml.saml1.core.AuthorizationDecisionStatement;
import org.opensaml.saml1.core.ConfirmationMethod;
import org.opensaml.saml1.core.Subject;
import org.opensaml.saml1.core.SubjectConfirmation;
import org.opensaml.saml1.core.SubjectStatement;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.AuthzDecisionStatement;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.KeyInfo;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.validation.ValidationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class AssertionWrapper can generate, sign, and validate both SAML v1.1
 * and SAML v2.0 assertions.
 * <p/>
 * Created on May 18, 2009
 */
public class AssertionWrapper {
    /**
     * Field log
     */
    private static final Log log = LogFactory.getLog(AssertionWrapper.class);

    /**
     * Raw SAML assertion data
     */
    private XMLObject xmlObject = null;

    /**
     * Typed SAML v1.1 assertion
     */
    private org.opensaml.saml1.core.Assertion saml1 = null;

    /**
     * Typed SAML v2.0 assertion
     */
    private org.opensaml.saml2.core.Assertion saml2 = null;

    /**
     * Which SAML specification to use (currently, only v1.1 and v2.0 are supported)
     */
    private SAMLVersion samlVersion;

    /**
     * Fully qualified class name of the SAML callback handler implementation to use.
     * NOTE: Each application should provide a unique implementation of this 
     * <code>Callback</code> that is able to extract any dynamic data from the local 
     * environment that should be included in the generated SAML statements.
     */
    private CallbackHandler samlCallbackHandler = null;
    
    /**
     * The Assertion as a DOM element
     */
    private Element assertionElement;
    
    /**
     * The SAMLKeyInfo object associated with the Subject KeyInfo
     */
    private SAMLKeyInfo subjectKeyInfo;
    
    /**
     * The SAMLKeyInfo object associated with the Signature on the Assertion
     */
    private SAMLKeyInfo signatureKeyInfo;

    /**
     * Constructor AssertionWrapper creates a new AssertionWrapper instance.
     *
     * @param element of type Element
     * @throws UnmarshallingException when
     */
    public AssertionWrapper(Element element) throws WSSecurityException {
        OpenSAMLUtil.initSamlEngine();
        
        this.xmlObject = OpenSAMLUtil.fromDom(element);
        if (xmlObject instanceof org.opensaml.saml1.core.Assertion) {
            this.saml1 = (org.opensaml.saml1.core.Assertion) xmlObject;
            samlVersion = SAMLVersion.VERSION_11;
        } else if (xmlObject instanceof org.opensaml.saml2.core.Assertion) {
            this.saml2 = (org.opensaml.saml2.core.Assertion) xmlObject;
            samlVersion = SAMLVersion.VERSION_20;
        } else {
            log.error(
                "AssertionWrapper: found unexpected type " 
                + (xmlObject != null ? xmlObject.getClass().getName() : xmlObject)
            );
        }
        
        assertionElement = element;
    }

    /**
     * Constructor AssertionWrapper creates a new AssertionWrapper instance.
     *
     * @param saml2 of type Assertion
     */
    public AssertionWrapper(org.opensaml.saml2.core.Assertion saml2) {
        this((XMLObject)saml2);
    }

    /**
     * Constructor AssertionWrapper creates a new AssertionWrapper instance.
     *
     * @param saml1 of type Assertion
     */
    public AssertionWrapper(org.opensaml.saml1.core.Assertion saml1) {
        this((XMLObject)saml1);
    }

    /**
     * Constructor AssertionWrapper creates a new AssertionWrapper instance.
     * This is the primary constructor.  All other constructor calls should
     * be routed to this method to ensure that the wrapper is initialized
     * correctly.
     *
     * @param xmlObject of type XMLObject
     */
    public AssertionWrapper(XMLObject xmlObject) {
        OpenSAMLUtil.initSamlEngine();
        
        this.xmlObject = xmlObject;
        if (xmlObject instanceof org.opensaml.saml1.core.Assertion) {
            this.saml1 = (org.opensaml.saml1.core.Assertion) xmlObject;
            samlVersion = SAMLVersion.VERSION_11;
        } else if (xmlObject instanceof org.opensaml.saml2.core.Assertion) {
            this.saml2 = (org.opensaml.saml2.core.Assertion) xmlObject;
            samlVersion = SAMLVersion.VERSION_20;
        } else {
            log.error(
                "AssertionWrapper: found unexpected type " 
                + (xmlObject != null ? xmlObject.getClass().getName() : xmlObject)
            );
        }
    }

    /**
     * Constructor AssertionWrapper creates a new AssertionWrapper instance.
     * This constructor is primarily called on the client side to initialize
     * the wrapper from a configuration file. <br>
     * NOTE: The OpenSaml library MUST be initialized prior to constructing an AssertionWrapper
     *
     * @param parms of type SAMLParms
     */
    public AssertionWrapper(SAMLParms parms) throws WSSecurityException {
        OpenSAMLUtil.initSamlEngine();
        
        // Set the SAML version
        if (parms.getSamlVersion().equalsIgnoreCase("1.1")) {
            samlVersion = SAMLVersion.VERSION_11;
        } else if (parms.getSamlVersion().equalsIgnoreCase("2.0")) {
            samlVersion = SAMLVersion.VERSION_20;
        } else {
            // Default to SAML v1.1
            samlVersion = SAMLVersion.VERSION_11;
        }

        //
        // Create the SAML callback that the handler will use to get the required data from the 
        // client application.
        //
        SAMLCallback[] samlCallbacks = new SAMLCallback[] { new SAMLCallback() };

        try {
            // Get the SAML source data using the currently configured callback implementation.
            if (samlCallbackHandler == null) {
                samlCallbackHandler = parms.getCallbackHandler();
            }

            samlCallbackHandler.handle(samlCallbacks);

        } catch (IOException e) {
            throw new IllegalStateException(
                "IOException while creating SAML assertion wrapper", e
            );
        } catch (UnsupportedCallbackException e) {
            throw new IllegalStateException(
                "UnsupportedCallbackException while creating SAML assertion wrapper", e
            );
        }

        if (samlVersion.equals(SAMLVersion.VERSION_11)) {
            // Build a SAML v1.1 assertion
            saml1 = SAML1ComponentBuilder.createSamlv1Assertion(parms.getIssuer());

            try {
                // Process the SAML authentication statement(s)
                List<AuthenticationStatement> authenticationStatements = 
                    SAML1ComponentBuilder.createSamlv1AuthenticationStatement(
                        samlCallbacks[0].getAuthenticationStatementData()
                    );
    
                // Process the SAML attribute statement(s)            
                List<org.opensaml.saml1.core.AttributeStatement> attributeStatements =
                        SAML1ComponentBuilder.createSamlv1AttributeStatement(
                            samlCallbacks[0].getAttributeStatementData()
                        );
    
                // Process the SAML authorization decision statement(s)
                List<org.opensaml.saml1.core.AuthorizationDecisionStatement> authDecisionStatements =
                        SAML1ComponentBuilder.createSamlv1AuthorizationDecisionStatement(
                            samlCallbacks[0].getAuthDecisionStatementData()
                        );
    
                // Build the complete assertion
                org.opensaml.saml1.core.Conditions conditions = 
                    SAML1ComponentBuilder.createSamlv1Conditions(samlCallbacks[0].getConditions());
                saml1.setConditions(conditions);
    
                // Add the SAML authentication statement(s) (if any)
                for (AuthenticationStatement authnStatement : authenticationStatements) {
                    saml1.getAuthenticationStatements().add(authnStatement);
                }
    
                // Add the SAML attribute statement(s) (if any)
                for (org.opensaml.saml1.core.AttributeStatement attrStatement : attributeStatements) {
                    saml1.getAttributeStatements().add(attrStatement);
                }
    
                // Add the SAML authorization decision statement(s) (if any)
                for (AuthorizationDecisionStatement authzStatement : authDecisionStatements) {
                    saml1.getAuthorizationDecisionStatements().add(authzStatement);
                }
            } catch (org.opensaml.xml.security.SecurityException ex) {
                throw new WSSecurityException(
                    "Error generating KeyInfo from signing credential", ex
                );
            }

            // Set the OpenSaml2 XMLObject instance
            xmlObject = saml1;

        } else if (samlVersion.equals(SAMLVersion.VERSION_20)) {
            // Build a SAML v2.0 assertion
            saml2 = SAML2ComponentBuilder.createAssertion();
            Issuer issuer = SAML2ComponentBuilder.createIssuer(parms.getIssuer());

            // Authn Statement(s)
            List<AuthnStatement> authnStatements = 
                SAML2ComponentBuilder.createAuthnStatement(
                    samlCallbacks[0].getAuthenticationStatementData()
                );

            // Attribute statement(s)
            List<org.opensaml.saml2.core.AttributeStatement> attributeStatements = 
                SAML2ComponentBuilder.createAttributeStatement(
                    samlCallbacks[0].getAttributeStatementData()
                );

            // AuthzDecisionStatement(s)
            List<AuthzDecisionStatement> authDecisionStatements =
                    SAML2ComponentBuilder.createAuthorizationDecisionStatement(
                        samlCallbacks[0].getAuthDecisionStatementData()
                    );

            // Build the SAML v2.0 assertion
            saml2.setIssuer(issuer);
            
            try {
                org.opensaml.saml2.core.Subject subject = 
                    SAML2ComponentBuilder.createSaml2Subject(samlCallbacks[0].getSubject());
                saml2.setSubject(subject);
            } catch (org.opensaml.xml.security.SecurityException ex) {
                throw new WSSecurityException(
                    "Error generating KeyInfo from signing credential", ex
                );
            }
            
            org.opensaml.saml2.core.Conditions conditions = 
                SAML2ComponentBuilder.createConditions(samlCallbacks[0].getConditions());
            saml2.setConditions(conditions);

            // Add the SAML authentication statemnt(s) (if any)
            for (AuthnStatement authnStatement : authnStatements) {
                saml2.getAuthnStatements().add(authnStatement);
            }

            // Add the SAML attribute statemnt(s) (if any)
            for (org.opensaml.saml2.core.AttributeStatement attributeStatement : attributeStatements) {
                saml2.getAttributeStatements().add(attributeStatement);
            }

            // Add the SAML authorization decision statemnt(s) (if any)
            for (AuthzDecisionStatement authorizationDecisionStatement : authDecisionStatements) {
                saml2.getAuthzDecisionStatements().add(authorizationDecisionStatement);
            }

            // Set the OpenSaml2 XMLObject instance
            xmlObject = saml2;
        }
    }

    /**
     * Method getSaml1 returns the saml1 of this AssertionWrapper object.
     *
     * @return the saml1 (type Assertion) of this AssertionWrapper object.
     */
    public org.opensaml.saml1.core.Assertion getSaml1() {
        return saml1;
    }

    /**
     * Method getSaml2 returns the saml2 of this AssertionWrapper object.
     *
     * @return the saml2 (type Assertion) of this AssertionWrapper object.
     */
    public org.opensaml.saml2.core.Assertion getSaml2() {
        return saml2;
    }

    /**
     * Method getXmlObject returns the xmlObject of this AssertionWrapper object.
     *
     * @return the xmlObject (type XMLObject) of this AssertionWrapper object.
     */
    public XMLObject getXmlObject() {
        return xmlObject;
    }

    /**
     * Method isCreated returns the created of this AssertionWrapper object.
     *
     * @return the created (type boolean) of this AssertionWrapper object.
     */
    public boolean isCreated() {
        return saml1 != null || saml2 != null;
    }


    /**
     * Create a DOM from the current XMLObject content. If the user-supplied doc is not null,
     * reparent the returned Element so that it is compatible with the user-supplied document.
     *
     * @param doc of type Document
     * @return Element
     */
    public Element toDOM(Document doc) throws WSSecurityException {
        return OpenSAMLUtil.toDom(xmlObject, doc);
    }

    /**
     * Method assertionToString ...
     *
     * @return String
     */
    public String assertionToString() throws WSSecurityException {
        Element element = toDOM(null);
        return DOM2Writer.nodeToString(element);
    }

    /**
     * Method getId returns the id of this AssertionWrapper object.
     *
     * @return the id (type String) of this AssertionWrapper object.
     */
    public String getId() {
        String id = null;
        if (saml2 != null) {
            id = saml2.getID();
        } else if (saml1 != null) {
            id = saml1.getID();
        } else {
            log.error("AssertionWrapper: unable to return ID - no saml assertion object");
        }
        if (id == null || id.length() == 0) {
            log.error("AssertionWrapper: ID was null, seeting a new ID value");
            id = UUIDGenerator.getUUID();
            if (saml2 != null) {
                saml2.setID(id);
            } else if (saml1 != null) {
                saml1.setID(id);
            }
        }
        return id;
    }

    /**
     * Method getIssuerString returns the issuerString of this AssertionWrapper object.
     *
     * @return the issuerString (type String) of this AssertionWrapper object.
     */
    public String getIssuerString() {
        if (saml2 != null && saml2.getIssuer() != null) {
            return saml2.getIssuer().getValue();
        } else if (saml1 != null) {
            return saml1.getIssuer();
        }
        log.error(
            "AssertionWrapper: unable to return Issuer string - no saml assertion "
            + "object or issuer is null"
        );
        return null;
    }

    /**
     * Method getConfirmationMethods returns the confirmationMethods of this 
     * AssertionWrapper object.
     *
     * @return the confirmationMethods of this AssertionWrapper object.
     */
    public List<String> getConfirmationMethods() {
        List<String> methods = new ArrayList<String>();
        if (saml2 != null) {
            org.opensaml.saml2.core.Subject subject = saml2.getSubject();
            List<org.opensaml.saml2.core.SubjectConfirmation> confirmations = 
                subject.getSubjectConfirmations();
            for (org.opensaml.saml2.core.SubjectConfirmation confirmation : confirmations) {
                methods.add(confirmation.getMethod());
            }
        } else if (saml1 != null) {
            List<SubjectStatement> subjectStatements = new ArrayList<SubjectStatement>();
            subjectStatements.addAll(saml1.getSubjectStatements());
            subjectStatements.addAll(saml1.getAuthenticationStatements());
            subjectStatements.addAll(saml1.getAttributeStatements());
            subjectStatements.addAll(saml1.getAuthorizationDecisionStatements());
            for (SubjectStatement subjectStatement : subjectStatements) {
                Subject subject = subjectStatement.getSubject();
                if (subject != null) {
                    SubjectConfirmation confirmation = subject.getSubjectConfirmation();
                    if (confirmation != null) {
                        XMLObject data = confirmation.getSubjectConfirmationData();
                        if (data instanceof ConfirmationMethod) {
                            ConfirmationMethod method = (ConfirmationMethod) data;
                            methods.add(method.getConfirmationMethod());
                        }
                        List<ConfirmationMethod> confirmationMethods = 
                            confirmation.getConfirmationMethods();
                        for (ConfirmationMethod confirmationMethod : confirmationMethods) {
                            methods.add(confirmationMethod.getConfirmationMethod());
                        }
                    }
                }
            }
        }
        return methods;
    }

    /**
     * Method isSigned returns the signed of this AssertionWrapper object.
     *
     * @return the signed (type boolean) of this AssertionWrapper object.
     */
    public boolean isSigned() {
        if (saml2 != null) {
            return saml2.isSigned() || saml2.getSignature() != null;
        } else if (saml1 != null) {
            return saml1.isSigned() || saml1.getSignature() != null;
        }
        return false;
    }

    /**
     * Method setSignature sets the signature of this AssertionWrapper object.
     *
     * @param signature the signature of this AssertionWrapper object.
     */
    public void setSignature(Signature signature) {
        if (xmlObject instanceof SignableSAMLObject) {
            SignableSAMLObject signableObject = (SignableSAMLObject) xmlObject;
            signableObject.setSignature(signature);
            signableObject.releaseDOM();
            signableObject.releaseChildrenDOM(true);
        } else {
            log.error("Attempt to sign an unsignable object " + xmlObject.getClass().getName());
        }
    }

    /**
     * Verify the signature of this assertion
     *
     * @throws ValidationException
     */
    public void verifySignature(Crypto crypto) throws WSSecurityException {
        Signature sig = null;
        if (saml2 != null && saml2.getSignature() != null) {
            sig = saml2.getSignature();
        } else if (saml1 != null && saml1.getSignature() != null) {
            sig = saml1.getSignature();
        }
        if (sig != null) {
            KeyInfo keyInfo = sig.getKeyInfo();
            SAMLKeyInfo samlKeyInfo = 
                SAMLUtil.getCredentialFromKeyInfo(keyInfo.getDOM(), crypto, null);
            if (samlKeyInfo == null) {
                throw new WSSecurityException(
                    WSSecurityException.FAILURE, "invalidSAMLsecurity",
                    new Object[]{"cannot get certificate or key"}
                );
            }
            SAMLSignatureProfileValidator validator = new SAMLSignatureProfileValidator();
            try {
                validator.validate(sig);
            } catch (ValidationException ex) {
                throw new WSSecurityException("SAML signature validation failed", ex);
            }
            
            BasicX509Credential credential = new BasicX509Credential();
            if (samlKeyInfo.getCerts() != null) {
                credential.setEntityCertificate(samlKeyInfo.getCerts()[0]);
            } else if (samlKeyInfo.getPublicKey() != null) {
                credential.setPublicKey(samlKeyInfo.getPublicKey());
            } else {
                throw new WSSecurityException(
                    WSSecurityException.FAILURE, "invalidSAMLsecurity",
                    new Object[]{"cannot get certificate or key"}
                );
            }
            SignatureValidator sigValidator = new SignatureValidator(credential);
            try {
                sigValidator.validate(sig);
            } catch (ValidationException ex) {
                throw new WSSecurityException("SAML signature validation failed", ex);
            }
            signatureKeyInfo = samlKeyInfo;
        } else {
            log.debug("AssertionWrapper: no signature to validate");
        }
    }
    
    /**
     * This method ensures that the Subject contains a KeyInfo for the holder-of-key confirmation
     * method, as required by the SAML Token spec. It then stores the SAMLKeyInfo object that
     * has been obtained for future processing by the SignatureProcessor.
     * @throws WSSecurityException
     */
    public void parseHOKSubject(Crypto crypto, CallbackHandler cb) throws WSSecurityException {
        String confirmMethod = null;
        List<String> methods = getConfirmationMethods();
        if (methods != null && methods.size() > 0) {
            confirmMethod = methods.get(0);
        }
        if (OpenSAMLUtil.isMethodHolderOfKey(confirmMethod)) {
            if (saml1 != null) {
                subjectKeyInfo = SAMLUtil.getCredentialFromSubject(saml1, crypto, cb);
            } else if (saml2 != null) {
                subjectKeyInfo = SAMLUtil.getCredentialFromSubject(saml2, crypto, cb);
            }
            if (subjectKeyInfo == null) {
                throw new WSSecurityException(WSSecurityException.FAILURE, "noKeyInSAMLToken");
            }
        }
    }
    

    /**
     * Method getSamlVersion returns the samlVersion of this AssertionWrapper object.
     *
     * @return the samlVersion (type SAMLVersion) of this AssertionWrapper object.
     */
    public SAMLVersion getSamlVersion() {
        if (samlVersion == null) {
            // Try to set the version.
            log.debug("The SAML version was null in getSamlVersion(). Recomputing SAML version...");
            if (saml1 != null && saml2 == null) {
                samlVersion = SAMLVersion.VERSION_11;
            } else if (saml1 == null && saml2 != null) {
                samlVersion = SAMLVersion.VERSION_20;
            } else {
                // We are only supporting SAML v1.1 or SAML v2.0 at this time.
                throw new IllegalStateException(
                    "Could not determine the SAML version number. Check your "
                    + "configuration and try again."
                );
            }
        }
        return samlVersion;
    }

    /**
     * Method setSamlVersion sets the samlVersion of this AssertionWrapper object.
     *
     * @param samlVersion the samlVersion of this AssertionWrapper object.
     */
    public void setSamlVersion(SAMLVersion samlVersion) {
        this.samlVersion = samlVersion;
    }

    /**
     * Method setSamlCallbackHandler sets the samlCallbackHandler of this AssertionWrapper object.
     *
     * @param samlCallbackHandler the samlCallbackHandler of this AssertionWrapper object.
     */
    public void setSamlCallbackHandler(CallbackHandler samlCallbackHandler) {
        this.samlCallbackHandler = samlCallbackHandler;
    }
    
    /**
     * Get the Assertion as a DOM Element.
     * @return the assertion as a DOM Element
     */
    public Element getElement() {
        return assertionElement;
    }
    
    /**
     * Get the SAMLKeyInfo associated with the signature of the assertion
     * @return the SAMLKeyInfo associated with the signature of the assertion
     */
    public SAMLKeyInfo getSignatureKeyInfo() {
        return signatureKeyInfo;
    }
    
    /**
     * Get the SAMLKeyInfo associated with the Subject KeyInfo
     * @return the SAMLKeyInfo associated with the Subject KeyInfo
     */
    public SAMLKeyInfo getSubjectKeyInfo() {
        return subjectKeyInfo;
    }

}
