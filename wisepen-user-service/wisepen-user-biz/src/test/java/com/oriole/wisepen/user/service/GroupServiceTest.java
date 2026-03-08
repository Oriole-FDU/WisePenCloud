package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.domain.entity.Group;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GroupServiceTest {
	@Autowired
	private GroupService groupService;

	@Test
	void testcreateGroup() {
		Group group=new Group();
		group.setName("Group1");
		group.setDescription("test");
		groupService.createGroup(group);
	}

	@Test
	void updateGroup() {
	}

	@Test
	void deleteGroup() {
	}

	@Test
	void getGroupIdsByUserId() {
	}

	@Test
	void getGroupIdsByUserIdAndType() {
	}

	@Test
	void getGroupById() {
	}
}