/*
 * Copyright 2017-2022 FUJITSU CLOUD TECHNOLOGIES LIMITED All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 *  OkHttp3 licence Information
 *
 *     Copyright 2019 Square, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.nifcloud.mbaas.core

import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL


/**
 * NCMB connection solving main class.
 *
 * This class is to do connection to NCMB and execute request, do receive response/error and
 * process callback after finishing receiving response.
 * Requests done based on okhttp3.
 *
 */

internal class NCMBConnection(request: NCMBRequest) {

    //time out millisecond from NIF Cloud mobile backend
    // var sConnectionTimeout = NCMB.TIMEOUT  //TODO
    //synchronized lock
    val lock = Any()
    //API request object
    var ncmbRequest: NCMBRequest
    //API response object
    lateinit var ncmbResponse: NCMBResponse

    /**
     * Constructor with NCMBRequest
     *
     */
    init{
        this.ncmbRequest = request
    }

    /**
     * Request NIF Cloud mobile backed api synchronously
     *
     * @return result object from NIF Cloud mobile backend
     * @throws NCMBException exception from NIF Cloud mobile backend
     */
    @Throws(NCMBException::class)
    fun sendRequest(): NCMBResponse {
        runBlocking {
            withContext(Dispatchers.Default) {
                val headers: Headers = createHeader()
                val client = OkHttpClient()

                println("Request Info (Sync):")
                println("scriptHeader: " + ncmbRequest.scriptHeader.toString())
                println("params: " + ncmbRequest.params.toString())
                println("querys: " + ncmbRequest.query.toString())
                println(ncmbRequest.url)
                println(headers)
                println(ncmbRequest.query)

                val body = ncmbRequest.params.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                var request: Request
                synchronized(lock) {
                    request = request(ncmbRequest.method, URL(ncmbRequest.url), headers, body)
                }
                val response = client.newCall(request).execute()
                //NCMBResponse 処理
                if (ncmbRequest.isFileGetRequest() || ncmbRequest.isScriptRequest()) {
                    ncmbResponse = NCMBResponseBuilder.buildFileScriptResponse(response)
                }else {
                    ncmbResponse = NCMBResponseBuilder.build(response)
                }

            }
        }
        //println()
        return ncmbResponse
    }

    /**
     * Request NIF Cloud mobile backed api synchronously (For file features)
     *
     * @return result object from NIF Cloud mobile backend
     * @throws NCMBException exception from NIF Cloud mobile backend
     */
    @Throws(NCMBException::class)
    fun sendRequestForUploadFile(): NCMBResponse {
        runBlocking {
            withContext(Dispatchers.Default) {
                val headers: Headers = createHeader()

                println("Request Info for File (Sync):")
                println("params: " + ncmbRequest.params.toString())
                println("querys: " + ncmbRequest.query.toString())
                println(ncmbRequest.url)
                println(headers)
                println(ncmbRequest.query)

                val request =
                    request(ncmbRequest.method, URL(ncmbRequest.url), headers, createFileRequestBody())
                val client = OkHttpClient().newCall(request)
                val response = client.execute()
                ncmbResponse = NCMBResponseBuilder.build(response)
            }
        }
        return ncmbResponse
    }

    /**
     * Request NIF Cloud mobile backed api synchronously (Excepts file features)
     *
     * @param callback NCMBCallback
     * @param responseHandler NCMBHandler
     * @throws NCMBException exception from NIF Cloud mobile backend
     */
    @Throws(NCMBException::class)
    fun sendRequestAsynchronously(callback: NCMBCallback, responseHandler: NCMBHandler) {

        val headers: Headers = createHeader()
        val client = OkHttpClient()

        println("Request Info (Async):")
        println("scriptHeader: " + ncmbRequest.scriptHeader.toString())
        println("params: " + ncmbRequest.params.toString())
        println("querys: " + ncmbRequest.query.toString())
        println(ncmbRequest.url)
        println(headers)
        val body = ncmbRequest.params.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = request(ncmbRequest.method, URL(ncmbRequest.url), headers, body)
        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    ncmbResponse = NCMBResponse.Failure(NCMBException(e))
                    responseHandler.doneSolveResponse(callback, ncmbResponse)
                }

                override fun onResponse(call: Call, response: Response) {
                    //NCMBResponse 処理
                    if (ncmbRequest.isFileGetRequest() || ncmbRequest.isScriptRequest()) {
                        ncmbResponse = NCMBResponseBuilder.buildFileScriptResponse(response)
                    }else {
                        ncmbResponse = NCMBResponseBuilder.build(response)
                    }
                    responseHandler.doneSolveResponse(callback, ncmbResponse)
                }

            })

        } catch (e: Exception) {
            e.printStackTrace()
            ncmbResponse = NCMBResponse.Failure(NCMBException(e))
            responseHandler.doneSolveResponse(callback, ncmbResponse)
        }
    }

    /**
     * Request NIF Cloud mobile backed api synchronously (For file)
     *
     * @param callback NCMBCallback
     * @param responseHandler NCMBHandler
     * @throws NCMBException exception from NIF Cloud mobile backend
     */
    @Throws(NCMBException::class)
    fun sendRequestAsynchronouslyForUploadFile(callback: NCMBCallback, responseHandler: NCMBHandler) {
        try {
            val headers: Headers = createHeader()

            println("Request Info for File(Async):")
            println("params: " + ncmbRequest.params.toString())
            println("querys: " + ncmbRequest.query.toString())
            println(ncmbRequest.url)
            println(headers)

            val request = request(ncmbRequest.method, URL(ncmbRequest.url), headers, createFileRequestBody())
            val client = OkHttpClient().newCall(request)

            client.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    ncmbResponse = NCMBResponse.Failure(NCMBException(e))
                    responseHandler.doneSolveResponse(callback, ncmbResponse)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        try{
                            //Convert error json from response body
                            var responseErrorString = response.body?.string()
                            var responseErrorJson = JSONObject(responseErrorString)
                            var fileException = NCMBException(responseErrorJson.getString("code"), responseErrorJson.getString("error"))
                            ncmbResponse = NCMBResponse.Failure(fileException)
                            responseHandler.doneSolveResponse(callback, ncmbResponse)
                        }catch (e:JSONException) {
                            var fileException = NCMBException(NCMBException.GENERIC_ERROR, "File save error: $response")
                            ncmbResponse = NCMBResponse.Failure(fileException)
                            responseHandler.doneSolveResponse(callback, ncmbResponse)
                        }
                    } else {
                        ncmbResponse = NCMBResponseBuilder.build(response)
                        responseHandler.doneSolveResponse(callback, ncmbResponse)
                    }
                }
            })

        } catch (e: Exception) {
            println(e.printStackTrace())
            ncmbResponse = NCMBResponse.Failure(NCMBException(e))
            responseHandler.doneSolveResponse(callback, ncmbResponse)
        }
    }

    /**
     * The key identified as an outlier depends on it on this map
     *
     * @return header map
     */
    fun createHeader():Headers{
        var headerMap = HashMap<String, String>()
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_APPS_SESSION_TOKEN)
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_APPLICATION_KEY)
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_TIMESTAMP)
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_SIGNATURE)
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_CONTENT_TYPE)
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_SDK_VERSION)
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_ACCESS_CONTROL_ALLOW_ORIGIN)
        headerMap = headerMapSet(headerMap, NCMBRequest.HEADER_OS_VERSION)
        if (!ncmbRequest.scriptHeader.isNullOrEmpty()) {
            headerMap = headerMapSetForScript(headerMap, ncmbRequest.scriptHeader!!)
        }
        return headerMap.toHeaders()
    }

    /**
     * Generate Request object while setting URL etc. in Builder class
     *
     * @param requestMethod  Method of request
     * @param requestUrl  Url of request
     * @param requestHeaders Headers info of request
     * @param requestBody  Header body info of request
     * @return request object
     */
    fun request(
        requestMethod: String,
        requestUrl: URL,
        requestHeaders: Headers,
        requestBody: RequestBody
    ):Request{
        lateinit var request: Request
        if (requestMethod == "GET") {
            request = Request.Builder()
                .url(requestUrl)
                .headers(requestHeaders)
                .build()
        }
        else if (requestMethod == "POST") {
            request = Request.Builder()
                .url(requestUrl)
                .headers(requestHeaders)
                .post(requestBody)
                .build()
        }
        else if (requestMethod == "PUT") {
            request = Request.Builder()
                .url(requestUrl)
                .headers(requestHeaders)
                .put(requestBody)
                .build()
        }
        else if (requestMethod == "DELETE") {
            request = Request.Builder()
                .url(requestUrl)
                .headers(requestHeaders)
                .delete(requestBody)
                .build()
        }
        return request
    }

    /**
     * Generate headers info map set
     *
     * @param headerMap  Headers map
     * @param headerInfo  Headers info
     * @return HashMap of headers info
     */
    fun headerMapSet(headerMap: HashMap<String, String>, headerInfo: String): HashMap<String, String>{
        val h_info = ncmbRequest.getRequestProperty(headerInfo)
        if (h_info != null) {
            headerMap.put(headerInfo, h_info)
        }
        return headerMap
    }

    fun headerMapSetForScript(headerMap: HashMap<String, String>, headerInfo: HashMap<String, String>): HashMap<String, String>{
        for (key : String in headerInfo.keys){
            val value = headerInfo.get(key)
            if(value != null) {
                headerMap.put(key, value)
            }
        }
        return headerMap
    }

    private fun createFileRequestBody(): RequestBody {
        //Get file from params
        if(ncmbRequest.params.has("file")) {
            val fileObj = ncmbRequest.params.get("file") as File
            if(ncmbRequest.params.has("acl")) {
                val fileAcl = ncmbRequest.params.get("acl") as JSONObject
                return MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", null,
                        fileObj.asRequestBody(createMimeType(fileObj.name).toMediaTypeOrNull()))
                    .addFormDataPart("acl",fileAcl.toString())
                    .build()
            }
            return MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", null,
                    fileObj.asRequestBody(createMimeType(fileObj.name).toMediaTypeOrNull()))
                .build()
        } else {
            throw NCMBException(NCMBException.GENERIC_ERROR, "A file need to be set to upload.")
        }
    }

    private fun createMimeType(fileName: String): String {
        //fileの拡張子毎のmimeTypeを作成
        var mimeType: String? = null
        if (fileName.lastIndexOf(".") != -1) {
            val extension = fileName.substring(fileName.lastIndexOf(".") + 1)
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        if (mimeType == null) {
            mimeType = "application/octet-stream"
        }
        return mimeType
    }

}
