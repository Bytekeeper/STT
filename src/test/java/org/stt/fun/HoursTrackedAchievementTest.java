/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.stt.fun;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stt.model.TimeTrackingItem;
import org.stt.time.DateTimes;

import java.time.LocalDateTime;

import static java.time.LocalDateTime.now;
import static org.hamcrest.CoreMatchers.is;

/**
 *
 * @author dante
 */
public class HoursTrackedAchievementTest extends AchievementTestBase {

	private HoursTrackedAchievement sut;
	private LocalDateTime base = DateTimes.preciseToSecond(now());

	@Before
	public void setup() {
		sut = new HoursTrackedAchievement(resourceBundle, 100);
		sut.start();
	}

	@Test
	public void shouldNotTriggerBelowThreshold() {
		// GIVEN
        sut.process(new TimeTrackingItem("", base, base.plusHours(99)));
        sut.done();

		// WHEN
		boolean achieved = sut.isAchieved();

		// THEN
		Assert.assertThat(achieved, is(false));
	}

	@Test
	public void shouldTriggerAboveThreshold() {
		// GIVEN
        sut.process(new TimeTrackingItem("", base, base.plusHours(99)));
        sut.process(new TimeTrackingItem("", base, base.plusHours(2)));
        sut.done();

		// WHEN
		boolean achieved = sut.isAchieved();

		// THEN
		Assert.assertThat(achieved, is(true));
	}

	@Test
	public void shouldTriggerEqualToThreshold() {
		// GIVEN
        sut.process(new TimeTrackingItem("", base, base.plusHours(78)));
        sut.process(new TimeTrackingItem("", base, base.plusHours(2)));
        sut.process(new TimeTrackingItem("", base, base.plusHours(20)));
        sut.done();

		// WHEN
		boolean achieved = sut.isAchieved();

		// THEN
		Assert.assertThat(achieved, is(true));
	}

	@Test
	public void shouldNotTriggerIfItemIsOngoing() {
		// GIVEN
        sut.process(new TimeTrackingItem("", base, base.plusHours(78)));
        sut.process(new TimeTrackingItem("", base.minusHours(30)));
        sut.done();

		// WHEN
		boolean achieved = sut.isAchieved();

		// THEN
		Assert.assertThat(achieved, is(false));
	}
}
