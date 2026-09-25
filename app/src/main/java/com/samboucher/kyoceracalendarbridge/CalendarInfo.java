package com.samboucher.kyoceracalendarbridge;

final class CalendarInfo {
    final long id;
    final String displayName;
    final String accountName;
    final String accountType;
    final int accessLevel;

    CalendarInfo(long id, String displayName, String accountName, String accountType, int accessLevel) {
        this.id = id;
        this.displayName = displayName;
        this.accountName = accountName;
        this.accountType = accountType;
        this.accessLevel = accessLevel;
    }

    @Override
    public String toString() {
        if (accountName == null || accountName.isEmpty()) {
            return displayName;
        }
        return displayName + " — " + accountName;
    }
}
