package com.example.temptrack.ui.favorite.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.temptrack.data.database.DataBaseResult
import com.example.temptrack.data.model.TempData
import com.example.temptrack.data.repositry.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class FavoriteViewModel (application: Application, private val repository: WeatherRepository) : ViewModel() {

        private val _favoriteList= MutableStateFlow<DataBaseResult>(DataBaseResult.Loading)
        val favoriteList=_favoriteList.asStateFlow()

        fun getFavoriteList()= viewModelScope.launch {
            repository.getAllFavorite()
                .catch { e->
                    _favoriteList.value=DataBaseResult.Error(e)
                }
                .collect {
                    list->
                    _favoriteList.value=DataBaseResult.Success(list)
                    Log.i("TAG", "getFavoriteList: $list")
            }
        }
    fun deleteFavorite(tempData: TempData){
       viewModelScope.launch {
           repository.deleteFavorite(tempData)
           getFavoriteList()

       }
    }
}