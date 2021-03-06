package com.bsb.hike.modules.animationModule;

import com.bsb.hike.R;
import com.bsb.hike.utils.Utils;

import android.content.Context;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;

public class HikeAnimationFactory
{	
	public static Animation getPulsatingDotAnimation(int initialOffset)
	{
		AnimationSet animSet = new AnimationSet(true);
		float a = 1.75f;
		float pivotX = 0.5f;
		float pivotY = 0.5f;
		
		Animation anim0 = new ScaleAnimation(1, a, 1, a, Animation.RELATIVE_TO_SELF, pivotX, Animation.RELATIVE_TO_SELF, pivotY);
		anim0.setStartOffset(initialOffset);
		anim0.setDuration(2500);
		anim0.setRepeatCount(Animation.INFINITE);
		animSet.addAnimation(anim0);

		Animation fade = new AlphaAnimation(1, 0);
		fade.setInterpolator(new AccelerateInterpolator(2f));
		fade.setStartOffset(1500);
		fade.setDuration(1000);
		fade.setRepeatCount(Animation.INFINITE);
		animSet.addAnimation(fade);
		return animSet;
	}

	public static AnimationSet getStickerShopIconAnimation(Context context)
	{
		final AnimationSet animSet = (AnimationSet) AnimationUtils.loadAnimation(context, R.anim.sticker_shop_icon_anim);

		animSet.setAnimationListener(new AnimationListener() {

			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				animation.reset();
				animation.start();
			}
		});
		return animSet;
	}
}
