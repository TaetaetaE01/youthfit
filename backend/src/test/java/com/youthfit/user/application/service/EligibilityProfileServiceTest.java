package com.youthfit.user.application.service;

import com.youthfit.common.exception.YouthFitException;
import com.youthfit.region.application.dto.result.RegionResult;
import com.youthfit.region.application.service.RegionQueryService;
import com.youthfit.region.domain.model.RegionLevel;
import com.youthfit.user.application.dto.command.UpdateEligibilityProfileCommand;
import com.youthfit.user.application.dto.result.EligibilityProfileResult;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DisplayName("EligibilityProfileService")
@ExtendWith(MockitoExtension.class)
class EligibilityProfileServiceTest {

    @InjectMocks
    private EligibilityProfileService eligibilityProfileService;

    @Mock
    private EligibilityProfileRepository eligibilityProfileRepository;

    @Mock
    private RegionQueryService regionQueryService;

    @Nested
    @DisplayName("findMyProfile - 프로필 조회")
    class FindMyProfile {

        @Test
        @DisplayName("프로필이 없으면 빈 프로필을 반환한다")
        void noProfile_returnsEmpty() {
            // given
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.empty());

            // when
            EligibilityProfileResult result = eligibilityProfileService.findMyProfile(1L);

            // then
            assertThat(result.userId()).isEqualTo(1L);
            assertThat(result.legalDongCode()).isNull();
            assertThat(result.sidoName()).isNull();
        }

        @Test
        @DisplayName("시군구 코드로 시도/시군구 이름을 조합한다")
        void sigunguCode_resolvesBothNames() {
            // given
            EligibilityProfile profile = EligibilityProfile.empty(1L);
            profile.changeLegalDongCode("1111000000");
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));
            given(regionQueryService.findByCode("1100000000"))
                    .willReturn(Optional.of(new RegionResult("1100000000", "서울특별시", RegionLevel.SIDO, null)));
            given(regionQueryService.findByCode("1111000000"))
                    .willReturn(Optional.of(new RegionResult("1111000000", "종로구", RegionLevel.SIGUNGU, "1100000000")));

            // when
            EligibilityProfileResult result = eligibilityProfileService.findMyProfile(1L);

            // then
            assertThat(result.sidoCode()).isEqualTo("1100000000");
            assertThat(result.sidoName()).isEqualTo("서울특별시");
            assertThat(result.sigunguName()).isEqualTo("종로구");
        }

        @Test
        @DisplayName("시도 코드만 있으면 시군구 이름은 null이다")
        void sidoOnlyCode_sigunguIsNull() {
            // given
            EligibilityProfile profile = EligibilityProfile.empty(1L);
            profile.changeLegalDongCode("3600000000");
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));
            given(regionQueryService.findByCode("3600000000"))
                    .willReturn(Optional.of(new RegionResult("3600000000", "세종특별자치시", RegionLevel.SIDO, null)));

            // when
            EligibilityProfileResult result = eligibilityProfileService.findMyProfile(1L);

            // then
            assertThat(result.sidoName()).isEqualTo("세종특별자치시");
            assertThat(result.sigunguName()).isNull();
        }
    }

    @Nested
    @DisplayName("updateMyProfile - 부분 수정")
    class UpdateMyProfile {

        @Test
        @DisplayName("전달된 필드만 수정하고 나머지는 유지한다")
        void partialUpdate_onlyChangesProvided() {
            // given
            EligibilityProfile profile = EligibilityProfile.empty(1L);
            profile.changeAge(25);
            profile.changeEmploymentKind(EmploymentKind.EMPLOYEE);
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));

            UpdateEligibilityProfileCommand command = new UpdateEligibilityProfileCommand(
                    null, 30, null, null, null, null, null, null, null
            );

            // when
            EligibilityProfileResult result = eligibilityProfileService.updateMyProfile(1L, command);

            // then
            assertThat(result.age()).isEqualTo(30);
            assertThat(result.employmentKind()).isEqualTo(EmploymentKind.EMPLOYEE);
            assertThat(profile.getAge()).isEqualTo(30);
            assertThat(profile.getEmploymentKind()).isEqualTo(EmploymentKind.EMPLOYEE);
        }

        @Test
        @DisplayName("legalDongCode가 DB에 없으면 INVALID_INPUT 예외가 발생한다")
        void invalidLegalDongCode_throws() {
            // given
            given(eligibilityProfileRepository.findByUserId(1L))
                    .willReturn(Optional.of(EligibilityProfile.empty(1L)));
            given(regionQueryService.findByCode("9999999999")).willReturn(Optional.empty());

            UpdateEligibilityProfileCommand command = new UpdateEligibilityProfileCommand(
                    "9999999999", null, null, null, null, null, null, null, null
            );

            // when & then
            assertThatThrownBy(() -> eligibilityProfileService.updateMyProfile(1L, command))
                    .isInstanceOf(YouthFitException.class);
        }

        @Test
        @DisplayName("프로필이 없으면 새로 생성한다")
        void noProfile_createsNew() {
            // given
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(eligibilityProfileRepository.save(any(EligibilityProfile.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            UpdateEligibilityProfileCommand command = new UpdateEligibilityProfileCommand(
                    null, 29, MaritalStatus.SINGLE, null, null, null, null, null, null
            );

            // when
            EligibilityProfileResult result = eligibilityProfileService.updateMyProfile(1L, command);

            // then
            assertThat(result.age()).isEqualTo(29);
            assertThat(result.maritalStatus()).isEqualTo(MaritalStatus.SINGLE);
        }

        @Test
        @DisplayName("incomeMin만 수정해도 기존 incomeMax는 유지한다")
        void incomeMinOnly_preservesIncomeMax() {
            // given
            EligibilityProfile profile = EligibilityProfile.empty(1L);
            profile.changeIncomeRange(null, 50000000L);
            given(eligibilityProfileRepository.findByUserId(1L)).willReturn(Optional.of(profile));

            UpdateEligibilityProfileCommand command = new UpdateEligibilityProfileCommand(
                    null, null, null, 10000000L, null, null, null, null, null
            );

            // when
            EligibilityProfileResult result = eligibilityProfileService.updateMyProfile(1L, command);

            // then
            assertThat(result.incomeMin()).isEqualTo(10000000L);
            assertThat(result.incomeMax()).isEqualTo(50000000L);
        }
    }
}
