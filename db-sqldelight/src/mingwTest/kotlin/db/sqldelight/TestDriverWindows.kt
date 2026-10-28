package db.sqldelight

import db.native.NativeDbDriver

internal actual val testDriver: db.DbDriver = NativeDbDriver
