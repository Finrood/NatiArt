import {Component, OnDestroy, OnInit} from '@angular/core';

import {ButtonComponent} from "../../../../../shared/components/button.component";

@Component({
  selector: 'app-top-banner',
  imports: [
    ButtonComponent
],
  templateUrl: './top-banner.component.html',
  styleUrl: './top-banner.component.css'
})
export class TopBannerComponent implements OnInit, OnDestroy {
  currentBannerIndex: number = 0;
  bannerImages: string[] = [
    "assets/img/a1.webp",
    "assets/img/a2.jpg",
    "assets/img/a3.jpg",
    "assets/img/a4.jpg"
  ];
  private bannerInterval: any;
  private readonly SLIDE_DURATION: number = 3500;
  private readonly TRANSITION_DURATION: number = 1000;

  ngOnInit() {
    this.startBannerInterval();
  }

  ngOnDestroy() {
    if (this.bannerInterval) {
      clearInterval(this.bannerInterval);
    }
  }

  prevSlide() {
    this.currentBannerIndex = (this.currentBannerIndex - 1 + this.bannerImages.length) % this.bannerImages.length;
    this.resetBannerInterval();
  }

  nextSlide() {
    this.currentBannerIndex = (this.currentBannerIndex + 1) % this.bannerImages.length;
    this.resetBannerInterval();
  }

  goToSlide(index: number) {
    this.currentBannerIndex = index;
    this.resetBannerInterval();
  }


  private startBannerInterval() {
    this.stopBannerInterval(); // Ensure any existing interval is stopped
    this.bannerInterval = setInterval(() => {
      this.nextSlide();
    }, this.SLIDE_DURATION + this.TRANSITION_DURATION);
  }

  private stopBannerInterval() {
    if (this.bannerInterval) {
      clearInterval(this.bannerInterval);
    }
  }

  private resetBannerInterval() {
    this.startBannerInterval();
  }
}
