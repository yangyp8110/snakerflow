/* Copyright 2012-2013 the original author or authors.
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
package org.snaker.engine.scheduling.quartz;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.calendar.BaseCalendar;
import org.quartz.impl.calendar.DailyCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snaker.engine.SnakerException;
import org.snaker.engine.helper.AssertHelper;
import org.snaker.engine.helper.ConfigHelper;
import org.snaker.engine.helper.StringHelper;
import org.snaker.engine.scheduling.IScheduler;
import org.snaker.engine.scheduling.JobEntity;

/**
 * quartz框架实现的调度实例
 * @author yuqs
 * @since 1.4
 */
public class QuartzScheduler implements IScheduler {
	private static final Logger log = LoggerFactory.getLogger(QuartzScheduler.class);
	private SchedulerFactory schedulerFactory = new StdSchedulerFactory();
	private boolean isUseCalendar = false;
	
	private Scheduler getScheduler() {
		try {
			Scheduler scheduler = schedulerFactory.getScheduler();
			String useCalendarStr = ConfigHelper.getProperty(CONFIG_USECALENDAR);
			boolean isUse = Boolean.parseBoolean(useCalendarStr);
			if(isUse) {
				BaseCalendar cal = build();
				if(cal != null) {
					scheduler.addCalendar(CALENDAR_NAME, cal, false, false);
					isUseCalendar = true;
				}
			}
			scheduler.start();
			return scheduler;
		} catch (SchedulerException e) {
			throw new SnakerException(e.getMessage(), e.getCause());
		}
	}
	
	/**
	 * 
	 */
	public void schedule(JobEntity entity) {
		AssertHelper.notNull(entity);
	    JobDataMap data = new JobDataMap(entity.getArgs());
	    data.put(KEY, entity.getId());
	    data.put(MODEL, entity.getModelName());
	    Class<? extends Job> jobClazz = null;
	    String jobId = "";
	    switch(entity.getJobType()) {
	    case 0:
	    	jobClazz = ExecutorJob.class;
	    	jobId = TYPE_EXECUTOR + entity.getTask().getId();
	    	break;
	    case 1:
	    	jobClazz = ReminderJob.class;
	    	jobId = TYPE_REMINDER + entity.getTask().getId();
	    	break;
	    }
	    if(jobClazz == null) {
	    	log.error("Quartz不支持的JOB类型:{}", entity.getJobType());
	    	return;
	    }
	    
	    JobDetail job = JobBuilder
	    		.newJob(jobClazz)
	    		.usingJobData(data)
	    		.withIdentity(jobId, GROUP)
	    		.build();
	    Trigger trigger = null;
	    TriggerBuilder<Trigger> builder = TriggerBuilder
	    		.newTrigger()
	    		.withIdentity(StringHelper.getPrimaryKey(), GROUP)
	    		.startAt(entity.getStartTime());
	    if(jobClazz == ReminderJob.class && entity.getPeriod() > 0) {
	    	int count = ConfigHelper.getNumerProperty(CONFIG_REPEAT);
	    	if(count <= 0) count = 1;
	    	builder.withSchedule(SimpleScheduleBuilder.
    				repeatMinutelyForTotalCount(count, entity.getPeriod()));
	    	if(isUseCalendar) {
	    		builder.modifiedByCalendar(CALENDAR_NAME);
	    	}
	    }
	    trigger = builder.build();
	    try {
	    	log.info("jobId:{} class:{} starting......", jobId, jobClazz);
			getScheduler().scheduleJob(job, trigger);
		} catch (SchedulerException e) {
			log.error(e.getMessage());
		}
	}

	public void delete(String key) {
		AssertHelper.notEmpty(key);
		try {
			log.info("jobId:{} deleted......", key);
			getScheduler().deleteJob(new JobKey(key, GROUP));
		} catch (SchedulerException e) {
			log.error(e.getMessage());
		}
	}
	
	private BaseCalendar build() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String holidays = ConfigHelper.getProperty(CONFIG_HOLIDAYS);
		String weeks = ConfigHelper.getProperty(CONFIG_WEEKS);
		String workTime = ConfigHelper.getProperty(CONFIG_WORKTIME);
		BaseCalendar holidayCal = null;
		if(StringHelper.isNotEmpty(holidays)) {
			String[] holidayArray = holidays.split(",");
			List<Calendar> calendars = new ArrayList<Calendar>();
			for(String holiday : holidayArray) {
				
			}
		}
		
		BaseCalendar weekdayCal = null;
		if(StringHelper.isNotEmpty(weeks)) {
			String[] weekArray = weeks.split(",");
			for(String week : weekArray) {
				
			}
		}
		
		DailyCalendar dailyCal = null;
		if(StringHelper.isNotEmpty(workTime)) {
			String[] workTimeArray = workTime.split("-");
			if(workTimeArray.length == 2) {
				dailyCal = new DailyCalendar(weekdayCal == null ? holidayCal : weekdayCal, workTimeArray[0], workTimeArray[1]);
			}
		}
		return dailyCal == null ? (weekdayCal == null ? holidayCal : weekdayCal) : dailyCal;
	}
}
