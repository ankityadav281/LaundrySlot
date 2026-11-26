package com.example.aasha.data.local

import androidx.room.TypeConverter
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.MachineType
import com.example.aasha.domain.model.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String = value?.name ?: SyncStatus.PENDING.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus =
        try {
            value?.let { SyncStatus.valueOf(it) } ?: SyncStatus.PENDING
        } catch (e: Exception) {
            SyncStatus.PENDING
        }

    @TypeConverter
    fun fromMachineStatus(value: MachineStatus?): String = value?.name ?: MachineStatus.AVAILABLE.name

    @TypeConverter
    fun toMachineStatus(value: String?): MachineStatus =
        try {
            value?.let { MachineStatus.valueOf(it) } ?: MachineStatus.AVAILABLE
        } catch (e: Exception) {
            MachineStatus.AVAILABLE
        }

    @TypeConverter
    fun fromMachineType(value: MachineType?): String = value?.name ?: MachineType.FRONT_LOAD.name

    @TypeConverter
    fun toMachineType(value: String?): MachineType =
        try {
            value?.let { MachineType.valueOf(it) } ?: MachineType.FRONT_LOAD
        } catch (e: Exception) {
            MachineType.FRONT_LOAD
        }

    @TypeConverter
    fun fromBookingStatus(value: BookingStatus?): String = value?.name ?: BookingStatus.UPCOMING.name

    @TypeConverter
    fun toBookingStatus(value: String?): BookingStatus =
        try {
            value?.let { BookingStatus.valueOf(it) } ?: BookingStatus.UPCOMING
        } catch (e: Exception) {
            BookingStatus.UPCOMING
        }
}
