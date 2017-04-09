/*
 * Copyright (c) 2017 Markus "Aneko" Isberg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package xyz.roolurker.colorify

import org.jetbrains.exposed.sql.Database

/**
 * Created with love by Aneko on 3/8/2017.
 */

fun main(args: Array<String>) {
	System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace")
	if (args.size != 4) {
		println("Invalid options, expected 'token' 'sql_address' 'sql_username' 'sql_password'")
	}
	val token: String = args[0]
	val sql_address: String = args[1]
	val sql_username: String = args[2]
	val sql_password: String = args[3]
	Database.connect(sql_address, driver = "com.mysql.jdbc.Driver",
			user = sql_username, password = sql_password)
	Colorify(token)
}