package com.lightchat.di

import android.content.Context
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.data.local.TokenManager
import com.lightchat.data.local.UserSession
import com.lightchat.data.local.dao.ConversationDao
import com.lightchat.data.local.dao.FriendRequestDao
import com.lightchat.data.local.dao.GroupDao
import com.lightchat.data.local.dao.MessageDao
import com.lightchat.data.local.dao.SyncStateDao
import com.lightchat.data.local.dao.UserDao
import com.lightchat.data.remote.AuthApiClient
import com.lightchat.data.repository.AuthRepository
import com.lightchat.data.repository.ConversationRepository
import com.lightchat.data.repository.MessageRepository
import com.lightchat.data.repository.UserRepository
import com.lightchat.im.ImClient
import com.lightchat.sync.EventProcessor
import com.lightchat.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context) = DatabaseHelper(context)

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context) = TokenManager(context)

    @Provides
    @Singleton
    fun provideUserSession(@ApplicationContext context: Context) = UserSession(context)

    @Provides @Singleton fun provideUserDao(db: DatabaseHelper) = UserDao(db)
    @Provides @Singleton fun provideMessageDao(db: DatabaseHelper) = MessageDao(db)
    @Provides @Singleton fun provideConversationDao(db: DatabaseHelper) = ConversationDao(db)
    @Provides @Singleton fun provideGroupDao(db: DatabaseHelper) = GroupDao(db)
    @Provides @Singleton fun provideSyncStateDao(db: DatabaseHelper) = SyncStateDao(db)
    @Provides @Singleton fun provideFriendRequestDao(db: DatabaseHelper) = FriendRequestDao(db)

    @Provides @Singleton fun provideAuthApiClient() = AuthApiClient()
    @Provides
    @Singleton
    fun provideUserRepository(dao: UserDao, userSession: UserSession) =
        UserRepository(dao, userSession)
    @Provides @Singleton fun provideMessageRepository(dao: MessageDao) = MessageRepository(dao)
    @Provides
    @Singleton
    fun provideConversationRepository(dao: ConversationDao, imClient: ImClient) =
        ConversationRepository(dao, imClient)

    @Provides
    @Singleton
    fun provideAuthRepository(
        userDao: UserDao,
        messageDao: MessageDao,
        conversationDao: ConversationDao,
        groupDao: GroupDao,
        syncStateDao: SyncStateDao,
        tokenManager: TokenManager,
        userSession: UserSession,
        apiClient: AuthApiClient
    ) = AuthRepository(
        userDao,
        messageDao,
        conversationDao,
        groupDao,
        syncStateDao,
        tokenManager,
        userSession,
        apiClient
    )

    @Provides @Singleton fun provideImClient() = ImClient()

    @Provides
    @Singleton
    fun provideEventProcessor(
        messageDao: MessageDao,
        conversationDao: ConversationDao,
        groupDao: GroupDao,
        userDao: UserDao,
        friendRequestDao: FriendRequestDao,
        syncStateDao: SyncStateDao
    ) = EventProcessor(
        messageDao,
        conversationDao,
        groupDao,
        userDao,
        friendRequestDao,
        syncStateDao
    )

    @Provides
    @Singleton
    fun provideSyncManager(imClient: ImClient, eventProcessor: EventProcessor) =
        SyncManager(imClient, eventProcessor)
}
