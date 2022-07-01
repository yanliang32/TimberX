package com.naman14.timberx.util;

import android.graphics.Rect;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import org.jetbrains.annotations.NotNull;

public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
  private final int space;

  public SpacesItemDecoration(int space) {
    this.space = space;
  }

  @Override
  public void getItemOffsets(
      @NotNull Rect outRect,
      @NotNull View view,
      @NotNull RecyclerView parent,
      @NotNull RecyclerView.State state) {
    outRect.left = space;
    outRect.right = space;
    outRect.bottom = space;

    // Add top margin only for the first item to avoid double space between items
    //    if (parent.getChildLayoutPosition(view) == 0) {
    //        outRect.top = space;
    //    } else {
    //        outRect.top = 0;
    //    }
  }
}
