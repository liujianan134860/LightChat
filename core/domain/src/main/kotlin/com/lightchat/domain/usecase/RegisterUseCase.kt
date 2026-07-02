package com.lightchat.domain.usecase

import com.lightchat.domain.repository.AuthRepositoryContract
import com.lightchat.model.User

class RegisterUseCase(private val repository: AuthRepositoryContract) {
    operator fun invoke(username: String, password: String, nickname: String): Result<User> {
        if (username.isBlank() || password.isBlank() || nickname.isBlank()) {
            return Result.failure(IllegalArgumentException("所有字段不能为空"))
        }
        return repository.register(username.trim(), password, nickname.trim())
    }
}
