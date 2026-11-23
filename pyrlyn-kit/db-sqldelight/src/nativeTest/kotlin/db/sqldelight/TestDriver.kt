package db.sqldelight

import db.DbDriver

/** The SQLite binding the tests run on: db-room's on macOS and Linux, db-native's on Windows. */
internal expect val testDriver: DbDriver
