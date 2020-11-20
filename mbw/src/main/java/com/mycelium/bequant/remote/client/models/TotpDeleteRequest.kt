/**
* Auth API
* Auth API<br> <a href='/changelog'>Changelog</a>
*
* The version of the OpenAPI document: v0.0.50
* 
*
* NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
* https://openapi-generator.tech
* Do not edit the class manually.
*/
package com.mycelium.bequant.remote.client.models



import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 
 * @param otpId 
 * @param passcode 
 */
data class TotpDeleteRequest (
    @JsonProperty("otp_id")
    val otpId: kotlin.Long,
    @JsonProperty("passcode")
    val passcode: kotlin.String
)

