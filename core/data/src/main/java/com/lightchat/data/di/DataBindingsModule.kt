package com.lightchat.data.di

import com.lightchat.data.repository.AuthRepository
import com.lightchat.data.repository.ConversationRepository
import com.lightchat.data.repository.MessageRepository
import com.lightchat.data.repository.UserRepository
import com.lightchat.domain.repository.AuthRepositoryContract
import com.lightchat.domain.repository.ConversationRepositoryContract
import com.lightchat.domain.repository.MessageRepositoryContract
import com.lightchat.domain.repository.UserRepositoryContract
import com.lightchat.domain.session.ConnectionController
import com.lightchat.im.ImClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds @Singleton abstract fun bindAuthRepository(impl: AuthRepository): AuthRepositoryContract
    @Binds @Singleton abstract fun bindUserRepository(impl: UserRepository): UserRepositoryContract
    @Binds @Singleton abstract fun bindConversationRepository(impl: ConversationRepository): ConversationRepositoryContract
    @Binds @Singleton abstract fun bindMessageRepository(impl: MessageRepository): MessageRepositoryContract
    @Binds @Singleton abstract fun bindConnectionController(impl: ImClient): ConnectionController
}
