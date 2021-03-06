/*
 * Copyright (C) 2018 Ferdinand Mütsch
 */

package com.github.n1try.popularmovies.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;

import com.github.n1try.popularmovies.R;
import com.github.n1try.popularmovies.api.TmdbApiService;
import com.github.n1try.popularmovies.model.Movie;
import com.github.n1try.popularmovies.model.MovieSortOrder;
import com.github.n1try.popularmovies.persistence.FavoriteMoviesContract;
import com.github.n1try.popularmovies.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.github.n1try.popularmovies.ui.MainActivity.KEY_HIDE_LOADING_DIALOG;
import static com.github.n1try.popularmovies.ui.MainActivity.KEY_PAGE;
import static com.github.n1try.popularmovies.ui.MainActivity.KEY_SORT_ORDER;

public class OverviewFragment extends Fragment {
    public interface OnMovieSelectedListener {
        public void onMovieSelected(Movie movie);
    }

    public interface OnDataLoadedListener {
        public void onDataLoaded();
    }

    private static final int MOVIE_LIST_LOADER_ID = 0;
    private static final int FAVORITE_MOVIE_LIST_LOADER_ID = 10;
    private static final String KEY_LIST_INSTANCE_STATE = "lv_state";
    private static final String KEY_LIST_MOVIES = "movie_list";

    @BindView(R.id.main_movies_gv)
    GridView moviesContainer;
    @BindView(R.id.main_offline_indicator_container)
    ViewGroup offlineContainer;
    @BindView(R.id.main_offline_indicator_iv)
    ImageView offlineIndicatorIv;

    private SharedPreferences prefs;
    private MovieItemAdapter movieAdapter;
    private ProgressDialog loadingDialog;
    private MovieSortOrder currentOrder;
    private MovieSortOrder currentLoaderState;
    private Parcelable listInstanceState;
    private List<Movie> movieList;

    private OnMovieSelectedListener mMovieSelectedListener;
    private OnDataLoadedListener mDataLoadedListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            listInstanceState = savedInstanceState.getParcelable(KEY_LIST_INSTANCE_STATE);
            movieList = savedInstanceState.getParcelableArrayList(KEY_LIST_MOVIES);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mMovieSelectedListener = (OnMovieSelectedListener) context;
        } catch (ClassCastException e) {
            Log.w(getClass().getSimpleName(), e.getMessage());
        }

        try {
            mDataLoadedListener = (OnDataLoadedListener) context;
        } catch (ClassCastException e) {
            Log.w(getClass().getSimpleName(), e.getMessage());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);
        ButterKnife.bind(this, view);

        prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        currentOrder = MovieSortOrder.valueOf(prefs.getString(KEY_SORT_ORDER, MovieSortOrder.POPULAR.name()));

        if (movieList == null || movieList.isEmpty()) {
            initOrRestartLoader();
        } else {
            postMoviesLoaded(movieList);
        }

        moviesContainer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Movie movieItem = (Movie) adapterView.getAdapter().getItem(i);
                if (mMovieSelectedListener != null)
                    mMovieSelectedListener.onMovieSelected(movieItem);
            }
        });

        resetScrollListener();

        loadingDialog = new ProgressDialog(getContext());
        loadingDialog.setTitle(getResources().getString(R.string.main_loading_title));
        loadingDialog.setMessage(getResources().getString(R.string.main_loading_text));
        loadingDialog.setCancelable(false);

        Drawable mIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_cloud_off_black_48dp);
        mIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.colorTextMedium), PorterDuff.Mode.SRC_IN);
        offlineIndicatorIv.setImageDrawable(mIcon);

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_LIST_INSTANCE_STATE, moviesContainer.onSaveInstanceState());
        outState.putParcelableArrayList(KEY_LIST_MOVIES, (ArrayList<Movie>) movieList);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_sort_popular:
                currentOrder = MovieSortOrder.POPULAR;
                prefs.edit().putString(KEY_SORT_ORDER, currentOrder.name()).apply();
                initOrRestartLoader();
                resetScrollListener();
                return true;
            case R.id.action_sort_rating:
                currentOrder = MovieSortOrder.TOP_RATED;
                prefs.edit().putString(KEY_SORT_ORDER, currentOrder.name()).apply();
                initOrRestartLoader();
                resetScrollListener();
                return true;
            case R.id.action_sort_favorites:
                currentOrder = MovieSortOrder.FAVORITE;
                prefs.edit().putString(KEY_SORT_ORDER, currentOrder.name()).apply();
                initOrRestartLoader();
                resetScrollListener();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void resetScrollListener() {
        moviesContainer.setOnScrollListener(new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {
                if (currentOrder == MovieSortOrder.FAVORITE) return false;
                getLoaderManager().restartLoader(MOVIE_LIST_LOADER_ID, createLoaderBundle(page, true), moviesFromApiLoaderCallbacks);
                return true;
            }
        });
    }

    /**
     * Triggers OnMovieSelectedListener::onMovieSelected with the Movie object contained
     * at position i of the current adapter's items list. Does nothing, if no item is present at position i.
     *
     * @param i Position of Movie to select
     */
    public void selectMovieByIndex(int i) {
        i = Math.max(i, 0);
        if (movieAdapter.getCount() > i - 1) {
            if (mMovieSelectedListener != null)
                mMovieSelectedListener.onMovieSelected(movieAdapter.getItem(i));
        }
    }

    public void selectMovie(Movie movie) {
        selectMovieByIndex(movieAdapter.getPosition(movie));
    }

    public MovieSortOrder getSortOrder() {
        return currentOrder;
    }

    private Bundle createLoaderBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_SORT_ORDER, currentOrder.name());
        return bundle;
    }

    private Bundle createLoaderBundle(int pageToLoad, boolean hideLoadingDialog) {
        Bundle bundle = createLoaderBundle();
        bundle.putInt(KEY_PAGE, pageToLoad);
        bundle.putBoolean(KEY_HIDE_LOADING_DIALOG, hideLoadingDialog);
        return bundle;
    }

    private void initOrRestartLoader() {
        LoaderManager lm = getLoaderManager();
        if (currentOrder == MovieSortOrder.FAVORITE) {
            if (lm.getLoader(FAVORITE_MOVIE_LIST_LOADER_ID) == null) {
                lm.initLoader(FAVORITE_MOVIE_LIST_LOADER_ID, createLoaderBundle(), favoriteMoviesFromDbLoaderCallbacks);
            } else {
                lm.restartLoader(FAVORITE_MOVIE_LIST_LOADER_ID, createLoaderBundle(), favoriteMoviesFromDbLoaderCallbacks);
            }
            lm.destroyLoader(MOVIE_LIST_LOADER_ID);
        } else {
            if (lm.getLoader(MOVIE_LIST_LOADER_ID) == null) {
                lm.initLoader(MOVIE_LIST_LOADER_ID, createLoaderBundle(), moviesFromApiLoaderCallbacks);
            } else {
                lm.restartLoader(MOVIE_LIST_LOADER_ID, createLoaderBundle(), moviesFromApiLoaderCallbacks);
            }
            lm.destroyLoader(FAVORITE_MOVIE_LIST_LOADER_ID);
        }
    }

    private LoaderManager.LoaderCallbacks<List<Movie>> moviesFromApiLoaderCallbacks = new LoaderManager.LoaderCallbacks<List<Movie>>() {
        @Override
        public Loader<List<Movie>> onCreateLoader(int id, Bundle bundle) {
            final MovieSortOrder order = MovieSortOrder.valueOf(bundle.getString(KEY_SORT_ORDER));
            final int page = bundle.getInt(KEY_PAGE, 1);
            final boolean hideLoadingDialog = bundle.getBoolean(KEY_HIDE_LOADING_DIALOG, false);

            return new AsyncTaskLoader<List<Movie>>(getContext()) {
                @Override
                public List<Movie> loadInBackground() {
                    switch (order) {
                        case POPULAR:
                            return TmdbApiService.getInstance(getContext()).getPopularMovies(page);
                        case TOP_RATED:
                            return TmdbApiService.getInstance(getContext()).getTopRatedMovies(page);
                        default:
                            return new ArrayList<>();
                    }
                }

                @Override
                protected void onStartLoading() {
                    if (!hideLoadingDialog) loadingDialog.show();
                    forceLoad();
                }
            };
        }

        @Override
        public void onLoadFinished(@NonNull Loader<List<Movie>> loader, List<Movie> movies) {
            postMoviesLoaded(movies);
        }

        @Override
        public void onLoaderReset(@NonNull Loader<List<Movie>> loader) {
        }
    };

    private LoaderManager.LoaderCallbacks<Cursor> favoriteMoviesFromDbLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
            return new CursorLoader(getContext(),
                    FavoriteMoviesContract.FavoriteMovieEntry.CONTENT_URI,
                    null,
                    null,
                    null,
                    null);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
            List<Movie> movies = new ArrayList<>();
            data.moveToFirst();
            for (int i = 0; i < data.getCount(); i++) {
                movies.add(FavoriteMoviesContract.FavoriteMovieEntry.movieFromCursor(data));
                data.moveToNext();
            }
            postMoviesLoaded(movies);
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        }
    };

    private void postMoviesLoaded(List<Movie> movies) {
        if (loadingDialog != null) loadingDialog.dismiss();

        if ((movies.isEmpty()) && !AndroidUtils.isNetworkAvailable(getContext())) {
            moviesContainer.setVisibility(View.GONE);
            offlineContainer.setVisibility(View.VISIBLE);
        } else {
            moviesContainer.setVisibility(View.VISIBLE);
            offlineContainer.setVisibility(View.GONE);
        }

        movieList = movies;
        if (currentOrder == MovieSortOrder.FAVORITE || currentOrder != currentLoaderState) {
            // Initial load
            movieAdapter = new MovieItemAdapter(getActivity().getApplicationContext(), movies);
            moviesContainer.setAdapter(movieAdapter);
            if (listInstanceState != null) {
                moviesContainer.onRestoreInstanceState(listInstanceState);
            }
        } else {
            // Load caused by infinite scrolling
            movieAdapter.addAll(movies);

            movieAdapter.notifyDataSetChanged();
        }

        currentLoaderState = currentOrder;
        if (mDataLoadedListener != null) mDataLoadedListener.onDataLoaded();
    }
}
