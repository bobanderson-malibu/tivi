/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.data.followedshows

import app.tivi.data.mappers.TraktListEntryToFollowedShowEntry
import app.tivi.data.mappers.TraktListEntryToTiviShow
import app.tivi.data.mappers.pairMapperOf
import app.tivi.data.models.FollowedShowEntry
import app.tivi.data.models.TiviShow
import app.tivi.data.util.bodyOrThrow
import app.tivi.data.util.withRetry
import com.uwetrottmann.trakt5.entities.ShowIds
import com.uwetrottmann.trakt5.entities.SyncItems
import com.uwetrottmann.trakt5.entities.SyncShow
import com.uwetrottmann.trakt5.entities.TraktList
import com.uwetrottmann.trakt5.entities.UserSlug
import com.uwetrottmann.trakt5.enums.Extended
import com.uwetrottmann.trakt5.enums.ListPrivacy
import com.uwetrottmann.trakt5.services.Users
import me.tatarka.inject.annotations.Inject
import retrofit2.awaitResponse

@Inject
class TraktFollowedShowsDataSource(
    private val usersService: Lazy<Users>,
    listEntryToShowMapper: TraktListEntryToTiviShow,
    listEntryToFollowedEntry: TraktListEntryToFollowedShowEntry,
) : FollowedShowsDataSource {
    companion object {
        private const val LIST_NAME = "Following"
    }

    private val listShowsMapper = pairMapperOf(listEntryToFollowedEntry, listEntryToShowMapper)

    override suspend fun addShowIdsToList(listId: Int, shows: List<TiviShow>) {
        val syncItems = SyncItems()
        syncItems.shows = shows.map { show ->
            SyncShow().apply {
                ids = ShowIds().apply {
                    trakt = show.traktId
                    imdb = show.imdbId
                    tmdb = show.tmdbId
                }
            }
        }
        withRetry {
            usersService.value
                .addListItems(UserSlug.ME, listId.toString(), syncItems)
                .awaitResponse()
                .bodyOrThrow()
        }
    }

    override suspend fun removeShowIdsFromList(listId: Int, shows: List<TiviShow>) {
        val syncItems = SyncItems()
        syncItems.shows = shows.map { show ->
            SyncShow().apply {
                ids = ShowIds().apply {
                    trakt = show.traktId
                    imdb = show.imdbId
                    tmdb = show.tmdbId
                }
            }
        }
        withRetry {
            usersService.value
                .deleteListItems(UserSlug.ME, listId.toString(), syncItems)
                .awaitResponse()
                .bodyOrThrow()
        }
    }

    override suspend fun getListShows(listId: Int): List<Pair<FollowedShowEntry, TiviShow>> {
        return withRetry {
            usersService.value
                .listItems(UserSlug.ME, listId.toString(), Extended.NOSEASONS)
                .awaitResponse()
                .let { listShowsMapper(it.bodyOrThrow()) }
        }
    }

    override suspend fun getFollowedListId(): TraktList {
        val fetchResult = withRetry {
            usersService.value
                .lists(UserSlug.ME)
                .awaitResponse()
                .let { response ->
                    response.bodyOrThrow().firstOrNull { it.name == LIST_NAME }
                }
        }

        if (fetchResult != null) {
            return fetchResult
        }

        return withRetry {
            usersService.value
                .createList(
                    UserSlug.ME,
                    TraktList().name(LIST_NAME)!!.privacy(ListPrivacy.PRIVATE),
                )
                .awaitResponse()
                .bodyOrThrow()
        }
    }
}