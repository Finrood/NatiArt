package com.portcelana.natiart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.portcelana.natiart.model.Package;
import com.portcelana.natiart.service.PackageManager;

@ExtendWith(MockitoExtension.class)
class PackageControllerPaginationTest {

    @Mock
    private PackageManager packageManager;

    @InjectMocks
    private PackageController packageController;

    @Test
    void getPackages_clampsOversizedSizeToMax() {
        final Package pack = new Package("box", 1.0f, 1.0f, 1.0f);
        when(packageManager.getPackages(any(Pageable.class))).thenReturn(List.of(pack));

        packageController.getPackages(0, Integer.MAX_VALUE);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(packageManager).getPackages(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void getPackages_clampsNegativePageToZero() {
        when(packageManager.getPackages(any(Pageable.class))).thenReturn(List.of());

        packageController.getPackages(-3, 20);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(packageManager).getPackages(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void getPackages_sortsByLabelInTheQuery() {
        when(packageManager.getPackages(any(Pageable.class))).thenReturn(List.of());

        packageController.getPackages(0, 20);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(packageManager).getPackages(captor.capture());
        final Sort.Order labelOrder = captor.getValue().getSort().getOrderFor("label");
        assertTrue(labelOrder != null && labelOrder.isAscending());
    }
}
