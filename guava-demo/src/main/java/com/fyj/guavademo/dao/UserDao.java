package com.fyj.guavademo.dao;

import com.fyj.guavademo.enetity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import javax.jws.soap.SOAPBinding;

public interface UserDao extends JpaRepository<User,String>, JpaSpecificationExecutor<User> {
}
