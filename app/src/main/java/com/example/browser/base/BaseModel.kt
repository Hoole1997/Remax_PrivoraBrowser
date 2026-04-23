package com.example.browser.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job

open class BaseModel : ViewModel() {

    private val jobs = mutableListOf<Job>()

    protected fun addJob(job: Job) {
        jobs.add(job)
    }

    override fun onCleared() {
        super.onCleared()
        // 取消所有正在进行的协程任务
        jobs.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
        jobs.clear()
    }
}