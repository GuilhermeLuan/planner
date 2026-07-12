package dev.guilhermeluan.planner.testsupport

import dev.guilhermeluan.planner.session.Account
import dev.guilhermeluan.planner.session.Planner
import dev.guilhermeluan.planner.storage.AccountEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.PlannerEntity

suspend fun PlannerDatabase.seed(account: Account, planner: Planner) {
    sessionDao().createLocalPlanner(
        AccountEntity(account.id, account.username, account.timezone, account.mustChangePassword),
        PlannerEntity(planner.id, planner.accountId),
    )
}
