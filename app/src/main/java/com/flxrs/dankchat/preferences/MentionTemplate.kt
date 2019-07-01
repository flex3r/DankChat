package com.flxrs.dankchat.preferences

enum class MentionTemplate(val value: String) {
	DEFAULT("$USER_VARIABLE "),
	WITH_COMMA("$USER_VARIABLE, "),
	WITH_AT("@$USER_VARIABLE "),
	WITH_AT_AND_COMMA("@$USER_VARIABLE, ");

	companion object {
		private val map = values().associateBy(MentionTemplate::value)
		fun fromString(value: String?) = map[value] ?: DEFAULT
	}
}

const val USER_VARIABLE = "name"