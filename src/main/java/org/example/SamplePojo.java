package org.example;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamplePojo {

    private String name;
    private int age;
    private String address;
    private String phoneNumber;
    private String email;
}
