package com.oriole.wisepen.user.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResp<T> implements Serializable {
	private long totalPage;
	private List<T> records;
}