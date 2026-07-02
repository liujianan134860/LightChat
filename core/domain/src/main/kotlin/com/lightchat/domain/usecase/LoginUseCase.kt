package com.lightchat.domain.usecase

import com.lightchat.domain.repository.AuthRepositoryContract
import com.lightchat.model.User

class LoginUseCase(private val repository: AuthRepositoryContract) {
    operator fun invoke(username: String, password: String): Result<User> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("账户和密码不能为空"))
        }
        return repository.login(username.trim(), password)
    }
}
