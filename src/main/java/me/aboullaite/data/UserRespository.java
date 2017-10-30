package me.aboullaite.data;

import org.springframework.data.jpa.repository.JpaRepository;

import me.aboullaite.entity.FbUser;

public interface UserRespository extends JpaRepository<FbUser, Long> {
		
}
