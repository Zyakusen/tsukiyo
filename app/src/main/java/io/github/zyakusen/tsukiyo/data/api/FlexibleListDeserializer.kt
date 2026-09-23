package io.github.zyakusen.tsukiyo.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 兼容 API 对列表字段的三种不规范序列化：
 * 1. 正常数组；
 * 2. 单元素被序列化成对象（如 other_language_editions_in_db 只有一个元素时）；
 * 3. 数组被序列化成以数字为 key 的对象（如某些作品的 language_editions）。
 */
class FlexibleListDeserializer : JsonDeserializer<List<*>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): List<*> {
        if (json.isJsonNull) return emptyList<Any>()
        val elementType: Type = (typeOfT as? ParameterizedType)?.actualTypeArguments?.firstOrNull() ?: Any::class.java

        if (json.isJsonArray) {
            val arr = json.asJsonArray
            val result = mutableListOf<Any>()
            for (i in 0 until arr.size()) {
                val item: Any = context.deserialize(arr[i], elementType)
                result.add(item)
            }
            return result
        }

        if (json.isJsonObject) {
            val obj = json.asJsonObject
            if (obj.size() == 0) return emptyList<Any>()
            val numericKeys = obj.keySet().all { it.toIntOrNull() != null }
            if (numericKeys) {
                val result = mutableListOf<Any>()
                for (e in obj.entrySet()) {
                    val item: Any = context.deserialize(e.value, elementType)
                    result.add(item)
                }
                return result
            }
            val item: Any = context.deserialize(json, elementType)
            return listOf(item)
        }

        return emptyList<Any>()
    }
}
