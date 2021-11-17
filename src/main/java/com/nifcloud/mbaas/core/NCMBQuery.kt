/*
* Copyright 2017-2021 FUJITSU CLOUD TECHNOLOGIES LIMITED All Rights Reserved.
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
*/
package com.nifcloud.mbaas.core

import com.nifcloud.mbaas.core.NCMBDateFormat.getIso8601
import kotlinx.serialization.json.JSON
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date


/**
 * NCMBQuery is used to search data from NIFCLOUD mobile backend
 */

//プライベートコンストラクターとしてcompanion object内にあるfor〇〇メソッドを用いて、インスタンスを取得する
class NCMBQuery<T : NCMBObject> private constructor(val mClassName: String, val service:NCMBServiceInterface<T>){
    private var mWhereConditions: JSONObject = JSONObject()
    private var order: List<String> = ArrayList()
    var limit: Int = 0 // default value is 0 (valid limit value is 1 to 1000)
        set(value) {
            if (value < 1 || value >1000 ) {
                throw NCMBException(
                    NCMBException.GENERIC_ERROR,
                    "Need to set limit value from 1 to 1000"
                )
            }
        }
    var skip: Int = 0 // default value is 0 (valid skip value is >0 )
        set(value) {
            if (value < 0 ) {
                throw NCMBException(
                    NCMBException.GENERIC_ERROR,
                    "Need to set skip value > 0"
                )
            }
        }
    companion object {
        fun forObject(className: String): NCMBQuery<NCMBObject> {
            return NCMBQuery<NCMBObject>(className, NCMBObjectService())
        }
    }


    /**　TODO
     * search data from NIFCLOUD mobile backend
     * @return NCMBObject(include extend class) list of search result
     * @throws NCMBException exception sdk internal or NIFCLOUD mobile backend
     */
    @Throws(NCMBException::class)
    fun find(): List<T> {
        return service.find(mClassName, query)
    }

    /**
     * search data from NIFCLOUD mobile backend asynchronously
     * @param callback executed callback after data search
     */
    fun findInBackground(findCallback: NCMBCallback) {
        service.findInBackground(mClassName, query, findCallback)
    }

    /**
     * get current search condition
     * @return current search condition
     */
    val query: JSONObject
        get() {
            val query = JSONObject()
            if (mWhereConditions != null && mWhereConditions.length() > 0) {
                query.put("where", mWhereConditions)
            }
            if (limit > 0 ) {
                query.put(NCMBQueryConstants.REQUEST_PARAMETER_LIMIT, limit)
            }
            if (skip > 0 ) {
                query.put(NCMBQueryConstants.REQUEST_PARAMETER_SKIP, skip)
            }

            if (order.size > 0) {
                query.put("order", order.joinToString(separator = "," ))
            }
            return  query
        }

    @Throws(JSONException::class)
    private fun convertConditionValue(value: Any): Any {
        return if (value is Date) {
            val dateJson = JSONObject("{'__type':'Date'}")
            val df: SimpleDateFormat = getIso8601()
            dateJson.put("iso", df.format(value as Date))
            dateJson
        } else {
            value
        }
    }

    /**
     * set the conditions to search the data that matches the value of the specified key
     * @param key field name to set the conditions
     * @param value condition value
     */
    fun whereEqualTo(key: String, value: Any) {
        try {
            mWhereConditions.put(key, convertConditionValue(value) )
        } catch (e: JSONException) {
            throw IllegalArgumentException(e.message)
        }
    }

    /**
     * set the conditions to search the data by ascending order with specified field name (key)
     * @param key field name for order by ascending
     */
    fun addOrderByAscending(key: String) {
        if(key != "") {
            order += key
        }
    }

    /**
     * set the conditions to search the data by descending order with specified field name (key)
     * @param key field name for order by ascending
     */
    fun addOrderByDescending(key: String) {
        if(key != "") {
            order += "-"+key
        }
    }


    /**
     * Constructor
     * @param className class name string for search data
     */
    init {
        mWhereConditions = JSONObject()
    }

}

