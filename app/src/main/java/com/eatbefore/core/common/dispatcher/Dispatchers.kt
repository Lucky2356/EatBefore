package com.eatbefore.core.common.dispatcher

import javax.inject.Qualifier

/** Qualifier for the IO dispatcher, used for database and file work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Qualifier for the Default dispatcher, used for CPU-bound work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
