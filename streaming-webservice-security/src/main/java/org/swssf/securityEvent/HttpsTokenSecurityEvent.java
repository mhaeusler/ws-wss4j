/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.swssf.securityEvent;

/**
 * @author $Author: giger $
 * @version $Revision: 272 $ $Date: 2010-12-23 14:30:56 +0100 (Thu, 23 Dec 2010) $
 */
public class HttpsTokenSecurityEvent extends TokenSecurityEvent {

    public enum AuthenticationType {
        HttpBasicAuthentication,
        HttpDigestAuthentication,
        HttpsClientCertificateAuthentication
    }

    private AuthenticationType authenticationType;
    private String issuerName;

    public HttpsTokenSecurityEvent(Event securityEventType) {
        super(securityEventType);
    }

    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(AuthenticationType authenticationType) {
        this.authenticationType = authenticationType;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }
}
